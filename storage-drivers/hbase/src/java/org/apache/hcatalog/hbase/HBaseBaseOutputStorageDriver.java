/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hcatalog.hbase;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.metastore.MetaStoreUtils;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.serde.Constants;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hcatalog.common.HCatConstants;
import org.apache.hcatalog.common.HCatUtil;
import org.apache.hcatalog.data.HCatRecord;
import org.apache.hcatalog.data.schema.HCatSchema;
import org.apache.hcatalog.mapreduce.HCatOutputStorageDriver;
import org.apache.hcatalog.mapreduce.HCatTableInfo;
import org.apache.hcatalog.mapreduce.OutputJobInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Base class share by both {@link HBaseBulkOutputStorageDriver} and {@link HBaseDirectOutputStorageDriver}
 */
abstract  class HBaseBaseOutputStorageDriver extends HCatOutputStorageDriver {
    protected HCatTableInfo tableInfo;
    protected ResultConverter converter;
    protected OutputJobInfo outputJobInfo;
    protected HCatSchema schema;
    protected HCatSchema outputSchema;

    @Override
    public void initialize(JobContext context, Properties hcatProperties) throws IOException {
        hcatProperties = (Properties)hcatProperties.clone();
        super.initialize(context, hcatProperties);


        String jobString = context.getConfiguration().get(HCatConstants.HCAT_KEY_OUTPUT_INFO);
        if( jobString == null ) {
            throw new IOException("OutputJobInfo information not found in JobContext. HCatInputFormat.setOutput() not called?");
        }

        outputJobInfo = (OutputJobInfo) HCatUtil.deserialize(jobString);
        //override table properties with user defined ones
        //TODO in the future we should be more selective on what to override
        hcatProperties.putAll(outputJobInfo.getProperties());
        outputJobInfo.getProperties().putAll(hcatProperties);
        hcatProperties = outputJobInfo.getProperties();


        String revision = outputJobInfo.getProperties().getProperty(HBaseConstants.PROPERTY_OUTPUT_VERSION_KEY);
        if(revision == null) {
            outputJobInfo.getProperties()
                         .setProperty(HBaseConstants.PROPERTY_OUTPUT_VERSION_KEY,
                                      new Path(outputJobInfo.getLocation()).getName());
        }

        tableInfo = outputJobInfo.getTableInfo();
        schema = tableInfo.getDataColumns();

        List<FieldSchema> fields = HCatUtil.getFieldSchemaList(outputSchema.getFields());
        hcatProperties.setProperty(Constants.LIST_COLUMNS,
                MetaStoreUtils.getColumnNamesFromFieldSchema(fields));
        hcatProperties.setProperty(Constants.LIST_COLUMN_TYPES,
                MetaStoreUtils.getColumnTypesFromFieldSchema(fields));

        //outputSchema should be set by HCatOutputFormat calling setSchema, prior to initialize being called
        converter = new HBaseSerDeResultConverter(schema,
                                                  outputSchema,
                                                  hcatProperties);
        context.getConfiguration().set(HBaseConstants.PROPERTY_OUTPUT_TABLE_NAME_KEY,tableInfo.getTableName());
    }

    @Override
    public void setSchema(JobContext jobContext, HCatSchema schema) throws IOException {
        this.outputSchema = schema;
    }

    @Override
    public WritableComparable<?> generateKey(HCatRecord value) throws IOException {
        //HBase doesn't use KEY as part of output
        return null;
    }

    @Override
    public Writable convertValue(HCatRecord value) throws IOException {
        return converter.convert(value);
    }

    @Override
    public void setPartitionValues(JobContext jobContext, Map<String, String> partitionValues) throws IOException {
        //no partitions for this driver
    }

    @Override
    public Path getWorkFilePath(TaskAttemptContext context, String outputLoc) throws IOException {
        return null;
    }

    @Override
    public void setOutputPath(JobContext jobContext, String location) throws IOException {
        //no output path
    }

    @Override
    public String getOutputLocation(JobContext jobContext, String tableLocation, List<String> partitionCols, Map<String, String> partitionValues, String dynHash) throws IOException {
        //TODO figure out a way to include user specified revision number as part of dir
        return new Path(tableLocation, Long.toString(System.currentTimeMillis())).toString();
    }
}
