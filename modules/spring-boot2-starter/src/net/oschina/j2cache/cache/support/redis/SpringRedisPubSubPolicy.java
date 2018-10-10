package net.oschina.j2cache.cache.support.redis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.oschina.j2cache.CacheProviderHolder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import net.oschina.j2cache.cluster.ClusterPolicy;
import net.oschina.j2cache.Command;
import net.oschina.j2cache.J2CacheConfig;
import net.oschina.j2cache.cache.support.util.SpringUtil;

/**
 * 使用spring redis实现订阅功能
 * @author zhangsaizz
 */
public class SpringRedisPubSubPolicy implements ClusterPolicy {
	
	private RedisTemplate<String, Serializable> redisTemplate;
	
	private net.oschina.j2cache.autoconfigure.J2CacheConfig config;
	private CacheProviderHolder holder;
	
	/**
	 * 是否是主动模式
	 */
	private static boolean isActive = false;
	
	private String channel = "j2cache_channel";
	
	@SuppressWarnings("unchecked")
	@Override
	public void connect(Properties props, CacheProviderHolder holder) {
		this.holder = holder;
		J2CacheConfig j2config = SpringUtil.getBean(J2CacheConfig.class);
		this.config =  SpringUtil.getBean(net.oschina.j2cache.autoconfigure.J2CacheConfig.class);
		this.redisTemplate = SpringUtil.getBean("j2CacheRedisTemplate", RedisTemplate.class);
		if("active".equals(config.getCacheCleanMode())) {
			isActive = true;
		}
		String channel_name = j2config.getL2CacheProperties().getProperty("channel");
		if(channel_name != null && !channel_name.isEmpty()) {
			this.channel = channel_name;
		}
		RedisMessageListenerContainer listenerContainer = SpringUtil.getBean("j2CacheRedisMessageListenerContainer", RedisMessageListenerContainer.class);
		
		listenerContainer.addMessageListener(new SpringRedisMessageListener(this, this.channel), new PatternTopic(this.channel));
		if(isActive || "blend".equals(config.getCacheCleanMode())) {
			//设置键值回调
			ConfigureNotifyKeyspaceEventsAction action = new ConfigureNotifyKeyspaceEventsAction();
			action.config(listenerContainer.getConnectionFactory().getConnection());
			
			String namespace = 	j2config.getL2CacheProperties().getProperty("namespace");
			String database = j2config.getL2CacheProperties().getProperty("database");
			String expired  = "__keyevent@" + (database == null || "".equals(database) ? "0" : database) + "__:expired";
			String del = "__keyevent@" + (database == null || "".equals(database) ? "0" : database) + "__:del";
			List<PatternTopic> topics = new ArrayList<>();
			topics.add(new PatternTopic(expired));
			topics.add(new PatternTopic(del));	
			listenerContainer.addMessageListener(new SpringRedisActiveMessageListener(this, namespace), topics);
		}

	}

	/**
	 * 删除本地某个缓存条目
	 * @param region 区域名称
	 * @param keys   缓存键值
	 */
	public void evict(String region, String... keys) {
		holder.getLevel1Cache(region).evict(keys);
	}

	/**
	 * 清除本地整个缓存区域
	 * @param region 区域名称
	 */
	public void clear(String region) {
		holder.getLevel1Cache(region).clear();
	}

//	@Override
//	public void sendEvictCmd(String region, String... keys) {
//		if(!isActive || "blend".equals(config.getCacheCleanMode())) {
//			String com = new Command(Command.OPT_EVICT_KEY, region, keys).json();
//	        redisTemplate.convertAndSend(this.channel, com);	
//		}
//
//	}
//	@Override
//	public void sendClearCmd(String region) {
//		if(!isActive || "blend".equals(config.getCacheCleanMode())) {
//			String com = new Command(Command.OPT_CLEAR_KEY, region, "").json();
//			redisTemplate.convertAndSend(this.channel, com);	
//		}
//	}

	@Override
    public void publish(Command cmd) {
		if(!isActive || "blend".equals(config.getCacheCleanMode())) {
			redisTemplate.convertAndSend(this.channel, cmd.json());
		}
    }
	

	@Override
	public void disconnect() {
		redisTemplate.convertAndSend(this.channel, Command.quit().json());
	}

	
}
