package com.boot.kafka;

import com.boot.common.LockUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class MyKafkaConsumer {
    //批次大小
    private static Integer batchSize = 5;
    //批次时间
    private static Integer batchTime = 5;

    @Resource
    private KafkaProperties kafkaProperties;

    private KafkaConsumer<String, String> kafkaConsumer;



    @PostConstruct
    public void init() {
        //配置消费者
        Map<String, Object> properties = kafkaProperties.buildConsumerProperties();
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");//指定消费组
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, batchSize); //指定批次消费条数
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); //禁用自动提交
        //建立消费者
        kafkaConsumer = new KafkaConsumer<>(properties);
    }

    public List<String> newConsumer() {
        List<String> result = new ArrayList<>();

        LockUtils.LOCK.lock();
        try {
            kafkaConsumer.subscribe(Collections.singletonList("samples-topic"));
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(batchTime));
            for (ConsumerRecord<String, String> record : records) {
                result.add(record.value());
                System.out.printf(record.topic() +
                                "一条新消息 offset = %d, key = %s, value = %s", record.offset(),
                        record.key(), record.value());
                System.out.println(record.topic() + "partition:" +
                        record.partition());
            }

            // 同步提交
            if (records.count() > 0) {
                kafkaConsumer.commitSync();
                System.out.println("批次提交");
            }
        } finally {
            LockUtils.LOCK.unlock();
        }
        return result;

    }





}
