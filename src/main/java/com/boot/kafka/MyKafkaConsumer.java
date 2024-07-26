package com.boot.kafka;

import com.boot.common.LockUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MyKafkaConsumer {
    //批次大小
    private static Integer batchSize = 5;
    //批次时间
    private static Integer batchTime = 5;

    @Resource
    private KafkaProperties kafkaProperties;

    @PostConstruct
    public void init() {

        //cousmer();
    }


    public List<String> cousmer() {
        try {
            List<String> result = new ArrayList<>();
            //配置消费者
            Map<String, Object> properties = kafkaProperties.buildConsumerProperties();
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");//指定消费组
            properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, batchSize); //指定批次消费条数
            properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); //禁用自动提交
            //建立消费者
            KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(properties);
            //获取所有partition信息
            List<PartitionInfo> partitionList = kafkaConsumer.partitionsFor("samples-topic");
            Map<TopicPartition, Integer> topicPartitionMap = new HashMap<>();
            partitionList.forEach(item
                    -> topicPartitionMap.put(new TopicPartition(item.topic(), item.partition()), item.partition()));
            //订阅topic并设置起始offset
            kafkaConsumer.assign(topicPartitionMap.keySet());
            topicPartitionMap.forEach(kafkaConsumer::seek);
            long starTime = System.currentTimeMillis();
            while (!LockUtils.LOCK.tryLock() && System.currentTimeMillis() - starTime > 10000L) {

            }
            Duration duration = Duration.ofSeconds(batchTime);
            Map<Integer, ConsumerRecord<String, String>> recordMap = new HashMap<>();
            ConsumerRecords<String, String> records = kafkaConsumer.poll(duration);
            int count = records.count();
            if (count > 0) {
                //处理数据
                records.forEach(item -> result.add(item.value()));
                //记录当前批次每个Partition最小offset
                for (ConsumerRecord<String, String> item : records) {
                    if (recordMap.containsKey(item.partition())) {
                        System.out.println("*******Consumer*****" + item.value());
                        ConsumerRecord<String, String> original = recordMap.get(item.partition());
                        if (item.offset() < original.offset()) {
                            recordMap.put(item.partition(), item);
                        }
                    } else {
                        recordMap.put(item.partition(), item);
                    }
                }
                //同步提交offset
                kafkaConsumer.commitSync();
                //正常提交后清除记录
                recordMap.clear();
            }

        } catch (Exception e) {
            recordMap.forEach((k, v) -> kafkaConsumer.seek(new TopicPartition(v.topic(), v.partition()), v.offset()));
        } finally {
            LockUtils.LOCK.unlock();
        }

        return result;

    }
}
