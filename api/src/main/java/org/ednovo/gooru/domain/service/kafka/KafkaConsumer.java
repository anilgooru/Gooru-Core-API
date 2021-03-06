package org.ednovo.gooru.domain.service.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

import org.ednovo.gooru.core.application.util.RequestUtil;
import org.ednovo.gooru.core.constant.ConfigConstants;
import org.ednovo.gooru.domain.service.setting.SettingService;
import org.ednovo.gooru.domain.service.user.impl.UserServiceImpl;
import org.ednovo.gooru.kafka.producer.KafkaProperties;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate4.HibernateTransactionManager;

public class KafkaConsumer implements Runnable {

	@Autowired
	private KafkaProperties kafkaProperties;

	private static ConsumerConnector consumer;

	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumer.class);

	@Autowired
	private SettingService settingService;

	private static KafkaStream m_stream;

	private static String restEndPoint;

	@PostConstruct
	public void init() {
		try {
			restEndPoint = settingService.getConfigSetting(ConfigConstants.GOORU_API_ENDPOINT);
			consumer = kafka.consumer.Consumer.createJavaConsumerConnector(createConsumerConfig());
		} catch (Exception e) {
			LOGGER.error("Serialization failed" + e);
		}
	}

	private ConsumerConfig createConsumerConfig() {

		Properties props = new Properties();
		props.put(KafkaProperties.ZK_CONSUMER_CONNECT, kafkaProperties.zkConsumerConnectValue);
		props.put(KafkaProperties.ZK_CONSUMER_GROUP, kafkaProperties.consumerGroupIdValue);
		props.put(KafkaProperties.ZK_SESSION_TIME_OUT_MS, KafkaProperties.ZK_SESSION_TIME_OUT_MS_VALUE);
		props.put(KafkaProperties.ZK_SYNCTIME_MS, KafkaProperties.ZK_SYNCTIME_MS_VALUE);
		props.put(KafkaProperties.AUTOCOMMIT_INTERVAL_MS, KafkaProperties.AUTOCOMMIT_INTERVAL_MS_VALUE);
		props.put(KafkaProperties.FETCH_SIZE, KafkaProperties.FETCH_SIZE_VALUE);
		props.put(KafkaProperties.AUTO_OFFSET_RESET, KafkaProperties.AUTO_OFFSET_RESET_VALUE);
		return new ConsumerConfig(props);

	}

	@Override
	public void run() {
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put(kafkaProperties.topicValue, 1);
		Map<String, List<KafkaStream<byte[], byte[]>>> listOfTopicsStreams = consumer.createMessageStreams(map);
		List<KafkaStream<byte[], byte[]>> listOfStream = listOfTopicsStreams.get(kafkaProperties.topicValue);
		m_stream = listOfStream.get(0);
		ConsumerIterator<byte[], byte[]> it = m_stream.iterator();

		while (it.hasNext()) {

			String message = new String(it.next().message());
			if (message.contains("{")) {
				try {
					JSONObject data = new JSONObject(message);
					String context = data.get("context").toString();
					JSONObject content = new JSONObject(context);
					String contentOid = content.get("contentGooruId").toString();
					String parentOid = content.get("parentContentGooruId").toString();
					String session = data.get("session").toString();
					JSONObject token = new JSONObject(session);
					String sessionToken = token.get("sessionToken").toString();

					if (data.get("eventName") != null && data.get("eventName").toString().equalsIgnoreCase("create.am:assessment-question")) {
						String jsonData = data.get("payLoadObject").toString();
						RequestUtil.executeRestAPI(jsonData, restEndPoint + "v2/assessment/" + contentOid + "/question", "POST", sessionToken);
					} else if (data.get("eventName") != null && data.get("eventName").toString().equalsIgnoreCase("update.am:assessment-question")) {
						String jsonData = data.get("payLoadObject").toString();
						RequestUtil.executeRestAPI(jsonData, restEndPoint + "v2/assessment/" + parentOid + "/question/" + contentOid, "PUT", sessionToken);
					} else if (data.get("eventName") != null && data.get("eventName").toString().equalsIgnoreCase("delete.am:assessment-question")) {
						RequestUtil.executeRestAPI(restEndPoint + "v2/assessment/" + parentOid + "/question/" + contentOid, "DELETE", sessionToken);
					}

				} catch (Exception e) {
					LOGGER.error("error" + e);
				}
			}

		}

	}
}
