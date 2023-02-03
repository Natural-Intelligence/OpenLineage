/*
/* Copyright 2018-2022 contributors to the OpenLineage project
/* SPDX-License-Identifier: Apache-2.0
*/

package io.openlineage.spark3.agent.lifecycle.plan;

import io.openlineage.client.OpenLineage;
import io.openlineage.spark.agent.Versions;
import io.openlineage.spark.agent.lifecycle.plan.JdbcRelationVisitor;
import io.openlineage.spark.api.OpenLineageContext;
import org.apache.spark.Partition;
import org.apache.spark.SparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.ExprId;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.execution.datasources.LogicalRelation;
import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions;
import org.apache.spark.sql.execution.datasources.jdbc.JDBCRelation;
import org.apache.spark.sql.internal.SessionState;
import org.apache.spark.sql.sources.BaseRelation;
import org.apache.spark.sql.types.*;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import scala.Option;
import scala.Tuple2;
import scala.collection.immutable.Seq$;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

class JdbcRelationVisitorTest {

    private static final String SOME_VERSION = "version_1";
    OpenLineageContext openLineageContext = mock(OpenLineageContext.class);
    SparkSession session = mock(SparkSession.class);
    JdbcRelationVisitor visitor = new JdbcRelationVisitor(openLineageContext);
    OpenLineage.DatasetVersionDatasetFacet facet = mock(OpenLineage.DatasetVersionDatasetFacet.class);
    SparkContext sparkContext = mock(SparkContext.class);
    SessionState sessionState = mock(SessionState.class);

    @BeforeEach
    void setup() {
        when(openLineageContext.getOpenLineage()).thenReturn(new OpenLineage(Versions.OPEN_LINEAGE_PRODUCER_URI));
        when(openLineageContext.getSparkContext()).thenReturn(sparkContext);
        when(openLineageContext.getSparkSession()).thenReturn(Optional.of(session));
        when(facet.getDatasetVersion()).thenReturn(SOME_VERSION);
        when(session.sessionState()).thenReturn(sessionState);
    }

    @Test
    void testJdbcRelationSimpleQuery() {
        JDBCRelation jdbcRelation = createJdbcRelation("select * from my_db.table1 limit 1", "jdbc:mysql://localhost:3306");
        LogicalPlan logicalPlan = createLogicalPlan(jdbcRelation);
        List<OpenLineage.InputDataset> datasets = visitor.apply(logicalPlan);
        Assert.assertEquals(1, datasets.size());
        Assert.assertEquals("my_db.table1", datasets.get(0).getFacets().getSymlinks().getIdentifiers().get(0).getName());
    }

    @Test
    void testJdbcRelationQueryGetDbNameFromUrl() {
        JDBCRelation jdbcRelation = createJdbcRelation("select * from table1 t1, table2 t2 where t1.key = t2.key", "jdbc:mysql://localhost:3306/my_db");
        LogicalPlan logicalPlan = createLogicalPlan(jdbcRelation);
        List<OpenLineage.InputDataset> datasets = visitor.apply(logicalPlan);
        Assert.assertEquals(2, datasets.size());
        Set<String> actualTableNames = datasets.stream()
                .flatMap(d -> d.getFacets().getSymlinks().getIdentifiers().stream()
                        .map(OpenLineage.SymlinksDatasetFacetIdentifiers::getName))
                .collect(Collectors.toSet());
        Assert.assertTrue(actualTableNames.containsAll(Arrays.asList("my_db.table1", "my_db.table2")));
    }

    @Test
    void testJdbcRelationJoinQuery() {
        JDBCRelation jdbcRelation = createJdbcRelation("select * from table1 t1 left join table2 t2 on t1.key = t2.key where t2.value is not null", "jdbc:mysql://localhost:3306/my_db");
        LogicalPlan logicalPlan = createLogicalPlan(jdbcRelation);
        List<OpenLineage.InputDataset> datasets = visitor.apply(logicalPlan);
        Assert.assertEquals(2, datasets.size());
        Set<String> actualTableNames = datasets.stream()
                .flatMap(d -> d.getFacets().getSymlinks().getIdentifiers().stream()
                        .map(OpenLineage.SymlinksDatasetFacetIdentifiers::getName))
                .collect(Collectors.toSet());
        Assert.assertTrue(actualTableNames.containsAll(Arrays.asList("my_db.table1", "my_db.table2")));
    }

    private JDBCRelation createJdbcRelation(String query, String url) {

        scala.collection.immutable.Map<String, String> params =
                scala.collection.immutable.Map$.MODULE$
                        .<String, String>newBuilder()
                        .$plus$eq(Tuple2.apply("driver", Driver.class.getName()))
                        .result();
        return new JDBCRelation(
                new StructType(
                        new StructField[]{
                                new StructField("key", IntegerType$.MODULE$, false, null),
                                new StructField("value", StringType$.MODULE$, false, null)
                        }),
                new Partition[]{},
                new JDBCOptions(
                        url,
                        query,
                        params),
                session);
    }

    private LogicalPlan createLogicalPlan(BaseRelation relation) {
        return new LogicalRelation(relation,
                Seq$.MODULE$
                        .<AttributeReference>newBuilder()
                        .$plus$eq(
                                new AttributeReference(
                                        "key",
                                        IntegerType$.MODULE$,
                                        false,
                                        null,
                                        ExprId.apply(1L),
                                        Seq$.MODULE$.<String>empty()))
                        .$plus$eq(
                                new AttributeReference(
                                        "value",
                                        StringType$.MODULE$,
                                        false,
                                        null,
                                        ExprId.apply(2L),
                                        Seq$.MODULE$.<String>empty()))
                        .result(),
                Option.empty(),
                false);
    }
}
