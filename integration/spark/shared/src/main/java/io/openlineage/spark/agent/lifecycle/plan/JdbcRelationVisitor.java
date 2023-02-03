/*
/* Copyright 2018-2023 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.spark.agent.lifecycle.plan;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.spark.agent.util.*;
import io.openlineage.spark.api.DatasetFactory;
import io.openlineage.spark.api.OpenLineageContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.execution.datasources.LogicalRelation;
import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions;
import org.apache.spark.sql.execution.datasources.jdbc.JDBCRelation;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class JdbcRelationVisitor extends AbstractRDDNodeVisitor<LogicalPlan, InputDataset> {

    public JdbcRelationVisitor(@NonNull OpenLineageContext context) {
        super(context, DatasetFactory.input(context));
    }

    @Override
    public boolean isDefinedAt(LogicalPlan x) {
        return x instanceof LogicalRelation
                && ((LogicalRelation) x).relation() instanceof JDBCRelation;
    }

    @Override
    public List<InputDataset> apply(LogicalPlan logRel) {
        JDBCRelation relation = (JDBCRelation) ((LogicalRelation) logRel).relation();
        // TODO- if a relation is composed of a complex sql query, we should attempt to
        // extract the
        // table names so that we can construct a true lineage
        String tableName =
                relation
                        .jdbcOptions()
                        .parameters()
                        .get(JDBCOptions.JDBC_TABLE_NAME())
                        .getOrElse(ScalaConversionUtils.toScalaFn(() -> "COMPLEX"));

        // strip the jdbc: prefix from the url. this leaves us with a url like
        // postgresql://<hostname>:<port>/<database_name>?params
        // we don't parse the URI here because different drivers use different
        // connection
        // formats that aren't always amenable to how Java parses URIs. E.g., the oracle
        // driver format looks like oracle:<drivertype>:<user>/<password>@<database>
        // whereas postgres, mysql, and sqlserver use the scheme://hostname:port/db
        // format.
        String url = JdbcUtils.sanitizeJdbcUrl(relation.jdbcOptions().url());
        log.debug("Extracting table named from query: {}", tableName);
        Set<String> tableNames = extractTableNames(tableName, url);
        log.debug("Extracted tableNames: {}", tableNames);

        return tableNames.stream()
                .map(t -> {
                    String namespace = t.substring(t.lastIndexOf(".") + 1);
                    DatasetIdentifier di = new DatasetIdentifier(tableName, namespace);
                    OpenLineage.DatasetFacetsBuilder datasetFacetsBuilder = context.getOpenLineage().newDatasetFacetsBuilder();
                    datasetFacetsBuilder.schema(PlanUtils.schemaFacet(context.getOpenLineage(), relation.schema()));
                    datasetFacetsBuilder.dataSource(PlanUtils.datasourceFacet(context.getOpenLineage(), di.getNamespace()));

                    OpenLineage.SymlinksDatasetFacetIdentifiers symLink = context
                            .getOpenLineage()
                            .newSymlinksDatasetFacetIdentifiersBuilder()
                            .name(t)
                            .namespace(namespace)
                            .build();

                    datasetFacetsBuilder.symlinks(context.getOpenLineage().newSymlinksDatasetFacet(Collections.singletonList(symLink)));
                    return datasetFactory.getDataset(di, datasetFacetsBuilder);
                }).collect(Collectors.toList());
    }

    private Set<String> extractTableNames(String query, String url) {
        Set<String> tableNames = new TableNameParser(query).tables().stream()
                .map(t -> t.replaceAll("`", "")).collect(Collectors.toSet());
        String dbName = extractDbNameFromUrl(url);
        if (dbName == null) {
            return tableNames;
        }
        return tableNames.stream()
                .map(t -> t.contains(".") ? t : dbName + "." + t)
                .collect(Collectors.toSet());
    }

    private String extractDbNameFromUrl(String url) {
        URI uri = URI.create(url);
        return (uri.getPath().startsWith("/")) ? uri.getPath().substring(1) : null;
    }
}
