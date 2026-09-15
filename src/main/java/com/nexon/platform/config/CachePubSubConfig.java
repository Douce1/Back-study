package com.nexon.platform.config;

import com.nexon.platform.service.LeaderboardService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CachePubSubConfig {

    public static final String CACHE_EVICT_TOPIC = "leaderboard:cache:evict";

    @Bean
    public ChannelTopic cacheEvictTopic() {
        return new ChannelTopic(CACHE_EVICT_TOPIC);
    }

    @Bean
    public MessageListenerAdapter cacheEvictListenerAdapter(LeaderboardService leaderboardService) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(leaderboardService, "handleCacheEvictMessage");
        adapter.setSerializer(new StringRedisSerializer());
        return adapter;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter cacheEvictListenerAdapter,
            ChannelTopic cacheEvictTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(cacheEvictListenerAdapter, cacheEvictTopic);
        return container;
    }
}