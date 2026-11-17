package dev.momentjin.gateway.mvc

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.AsyncProxyManager
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedisBucketConfiguration {
    @Bean(destroyMethod = "shutdown")
    fun bucketRedisClient(
        @Value("\${spring.data.redis.host}") host: String,
        @Value("\${spring.data.redis.port}") port: Int,
    ): RedisClient = RedisClient.create(
        RedisURI.Builder.redis(host, port).withTimeout(Duration.ofSeconds(2)).build(),
    )

    @Bean(destroyMethod = "close")
    fun bucketRedisConnection(client: RedisClient): StatefulRedisConnection<String, ByteArray> =
        client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))

    @Bean
    fun redisProxyManager(connection: StatefulRedisConnection<String, ByteArray>): AsyncProxyManager<String> =
        Bucket4jLettuce.casBasedBuilder(connection)
            .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
            .build()
            .asAsync()
}
