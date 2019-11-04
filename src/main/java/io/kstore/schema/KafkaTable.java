/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kstore.schema;

import io.kcache.Cache;
import io.kcache.KafkaCache;
import io.kcache.KafkaCacheConfig;
import io.kcache.utils.Caches;
import io.kcache.utils.InMemoryCache;
import io.kstore.serialization.KryoSerde;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.kafka.common.serialization.Serdes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class KafkaTable implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaTable.class);

    private KafkaSchemaValue schemaValue;
    private Cache<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> rows;

    public KafkaTable(Configuration config, KafkaSchemaValue schemaValue) {
        this.schemaValue = schemaValue;
        TableName tableName = TableName.valueOf(schemaValue.getTableName());
        int epoch = schemaValue.getEpoch();
        String topic = tableName.getQualifierAsString() + "_" + epoch;
        Map<String, Object> configs = new HashMap<>();
        // TODO pass in
        String groupId = "kstore-1";
        configs.put(KafkaCacheConfig.KAFKACACHE_TOPIC_CONFIG, topic);
        configs.put(KafkaCacheConfig.KAFKACACHE_GROUP_ID_CONFIG, groupId);
        configs.put(KafkaCacheConfig.KAFKACACHE_CLIENT_ID_CONFIG, groupId + "-" + topic);
        // TODO
        configs.put(KafkaCacheConfig.KAFKACACHE_BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        Cache<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> rowMap = new KafkaCache<>(
            new KafkaCacheConfig(configs), Serdes.ByteArray(), new KryoSerde<>(), null,
            new InMemoryCache<>(new ConcurrentSkipListMap<>(Bytes.BYTES_COMPARATOR)));
        this.rows = Caches.concurrentCache(rowMap);

        //this.data = new InMemoryCache<>(new TreeMap<>(Bytes.BYTES_COMPARATOR));
        /*
        configs.put(KafkaCacheConfig.KAFKACACHE_ENABLE_OFFSET_COMMIT_CONFIG, true);
        String enableRocksDbStr = (String) configs.getOrDefault(KarelDbConfig.ROCKS_DB_ENABLE_CONFIG, "true");
        boolean enableRocksDb = Boolean.parseBoolean(enableRocksDbStr);
        String rootDir = (String) configs.getOrDefault(
            KarelDbConfig.ROCKS_DB_ROOT_DIR_CONFIG, KarelDbConfig.ROCKS_DB_ROOT_DIR_DEFAULT);
        Comparator<byte[]> cmp = new AvroKeyComparator(schemas.left);
        Cache<byte[], byte[]> cache = enableRocksDb
            ? new RocksDBCache<>(topic, "rocksdb", rootDir, Serdes.ByteArray(), Serdes.ByteArray(), cmp)
            : new InMemoryCache<>(cmp);

         */
    }

    public void init() {
        rows.init();
        LOG.info("Initialized table: {}, version: {}", schemaValue.getTableName(), schemaValue.getVersion());
    }

    public KafkaSchemaValue getSchemaValue() {
        return schemaValue;
    }

    public void setSchemaValue(KafkaSchemaValue schemaValue) {
        this.schemaValue = schemaValue;
    }

    public Cache<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> getRows() {
        return rows;
    }

    public void close() throws IOException {
        if (rows != null) {
            rows.close();
            rows = null;
        }
    }
}
