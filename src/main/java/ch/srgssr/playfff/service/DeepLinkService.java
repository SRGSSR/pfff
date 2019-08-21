package ch.srgssr.playfff.service;

import ch.srg.il.domain.v2_0.ModuleConfig;
import ch.srg.il.domain.v2_0.ModuleConfigList;
import ch.srgssr.playfff.helper.BaseResourceString;
import ch.srgssr.playfff.model.DeepLinkJSContent;
import ch.srgssr.playfff.model.Environment;
import ch.srgssr.playfff.model.playportal.PlayTopic;
import ch.srgssr.playfff.utils.Sha1;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
@Service
@EnableCaching
public class DeepLinkService {
    private static final Logger logger = LoggerFactory.getLogger(DeepLinkService.class);

    private static final String DeepLinkCacheName = "DeeplinkParsePlayUrlJSContent";

    private RestTemplate restTemplate;

    private Set<Environment> pullEnvironmentSet = new HashSet<>();

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    private IntegrationLayerRequest integrationLayerRequest;

    public DeepLinkService(RestTemplateBuilder restTemplateBuilder,
                           @Value("${DEEP_LINK_ENVIRONMENTS:PROD}") String environments) {
        restTemplate = restTemplateBuilder.build();

        String[] environmentStrings = environments.split(",");
        for (String environmentString : environmentStrings) {
            pullEnvironmentSet.add(Environment.fromValue(environmentString));
        }
    }

    @Cacheable(DeepLinkCacheName)
    public DeepLinkJSContent getParsePlayUrlJSContent() {
        return refreshParsePlayUrlJSContent();
    }

    @CachePut(DeepLinkCacheName)
    public synchronized DeepLinkJSContent refreshParsePlayUrlJSContent() {
        String javascript = BaseResourceString.getString(applicationContext, "parsePlayUrl.js");

        Map<String, Map<String, Map<String, String>>> tvGlobalTopicsMap = new HashMap<>();
        Map<String, Map<String, Map<String, String>>> tvGlobalEventsMap = new HashMap<>();

        for (Environment environment : pullEnvironmentSet) {
            Map<String, String> buProdMap = new HashMap<>();
            buProdMap.put("srf", "www.srf.ch");
            buProdMap.put("rts", "www.rts.ch");
            buProdMap.put("rsi", "www.rsi.ch");
            buProdMap.put("rtr", "www.rtr.ch");
            buProdMap.put("swi", "play.swissinfo.ch");

            Map<String, String> buStageMap = new HashMap<>();
            buStageMap.put("srf", "srgplayer-srf.stage.srf.ch");
            buStageMap.put("rts", "srgplayer-rts.stage.srf.ch");
            buStageMap.put("rsi", "srgplayer-rsi.stage.srf.ch");
            buStageMap.put("rtr", "srgplayer-rtr.stage.srf.ch");
            buStageMap.put("swi", "srgplayer-swi.stage.srf.ch");

            Map<String, String> buTestMap = new HashMap<>();
            buTestMap.put("srf", "srgplayer-srf.test.srf.ch");
            buTestMap.put("rts", "srgplayer-rts.test.srf.ch");
            buTestMap.put("rsi", "srgplayer-rsi.test.srf.ch");
            buTestMap.put("rtr", "srgplayer-rtr.test.srf.ch");
            buTestMap.put("swi", "srgplayer-swi.test.srf.ch");

            Map<Environment, Map<String, String>> buMap = new HashMap<>();
            buMap.put(Environment.PROD, buProdMap);
            buMap.put(Environment.STAGE, buStageMap);
            buMap.put(Environment.TEST, buTestMap);
            buMap.put(Environment.MMF, new HashMap<>());

            Map<String, Map<String, String>> tvTopicsMap = getTvTopicList(environment, buMap);

            if (tvTopicsMap.size() > 0) {
                tvGlobalTopicsMap.put(environment.getPrettyName(), tvTopicsMap);
            }

            // Get event module list
            Map<String, Map<String, String>> tvEventsMap = new HashMap<>();

            for (Map.Entry<String, String> bu : buProdMap.entrySet()) {
                ModuleConfigList moduleConfigList = integrationLayerRequest.getEvents(bu.getKey(), environment);
                if (moduleConfigList != null) {
                    Map<String, String> tvEventsSubMap = new HashMap<>();

                    for (int i = 0; i < moduleConfigList.getModuleConfigList().size(); i++) {
                        ModuleConfig moduleConfig = moduleConfigList.getModuleConfigList().get(i);
                        tvEventsSubMap.put(moduleConfig.getSeoName(), moduleConfig.getId());
                    }

                    tvEventsMap.put(bu.getKey(), tvEventsSubMap);
                }
            }

            if (tvEventsMap.size() > 0) {
                tvGlobalEventsMap.put(environment.getPrettyName(), tvEventsMap);
            }
        }

        ObjectMapper mapperObj = new ObjectMapper();

        String tvTopics = null;
        try {
            tvTopics = mapperObj.writeValueAsString(tvGlobalTopicsMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (tvTopics != null) {
            javascript = javascript.replaceAll("\\/\\* INJECT TVTOPICS OBJECT \\*\\/", "var tvTopics = " + tvTopics + ";");
        }

        String tvEvents = null;
        try {
            tvEvents = mapperObj.writeValueAsString(tvGlobalEventsMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (tvEvents != null) {
            javascript = javascript.replaceAll("\\/\\* INJECT TVEVENTS OBJECT \\*\\/", "var tvEvents = " + tvEvents + ";");
        }

        String buildHash = "NO_SHA1";
        try {
            buildHash = Sha1.sha1(javascript);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            logger.warn("sha1", e);
        }
        Date buildDate = new Date();

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Zurich"));
        String strDate = dateFormat.format(buildDate);
        javascript = javascript.replaceAll("var parsePlayUrlBuild = \"mmf\";", "var parsePlayUrlBuild = \"" + buildHash + "\";\nvar parsePlayUrlBuildDate = \"" + strDate + "\";");

        return new DeepLinkJSContent(javascript, buildHash);
    }

    private Map<String, Map<String, String>> getTvTopicList(Environment environment, Map<Environment, Map<String, String>> buMap) {
        Map<String, Map<String, String>> tvTopicsMap = new HashMap<>();

        for (Map.Entry<String, String> bu : buMap.get(environment).entrySet()) {
            URI tvTopicListUri = null;
            try {
                tvTopicListUri = new URI("https", null, bu.getValue(), 443, "/play/tv/topicList",
                        null, null);

                ResponseEntity<PlayTopic[]> tvTopicListResponseEntity = restTemplate.exchange(tvTopicListUri, HttpMethod.GET, null, PlayTopic[].class);

                if (tvTopicListResponseEntity.getBody() != null) {
                    PlayTopic[] tvTopicList = tvTopicListResponseEntity.getBody();
                    Map<String, String> tvTopicsSubMap = new HashMap<>();

                    for (PlayTopic playTopic : tvTopicList) {
                        tvTopicsSubMap.put(playTopic.getUrlEncodedTitle(), playTopic.getId());
                    }

                    tvTopicsMap.put(bu.getKey(), tvTopicsSubMap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return tvTopicsMap;
    }

}
