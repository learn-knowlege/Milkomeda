package com.github.yizzuide.milkomeda.universe.parser.yml;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * YmlParser
 * yml解析器
 *
 * @author yizzuide
 * @since 3.0.0
 * Create at 2020/03/29 19:24
 */
public class YmlParser {
    /**
     * 解析别名
     * @param nodeMap  拥有别名的节点Map，无别名键时添加本身
     * @return  别名Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, YmlAliasNode> parseAliasMap(Map<String, Object> nodeMap) {
        Map<String, YmlAliasNode> aliasNodeMap = new HashMap<>();
        for (Map.Entry<String, Object> node : nodeMap.entrySet()) {
            String key = node.getKey();
            Object value = node.getValue();
            // 别名替换
            if (value instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) value;
                key = String.valueOf(valueMap.keySet().toArray()[0]);
                value = valueMap.get(key);
            }
            YmlAliasNode ymlAliasNode = new YmlAliasNode();
            ymlAliasNode.setKey(key);
            ymlAliasNode.setValue(value);
            aliasNodeMap.put(node.getKey(), ymlAliasNode);
        }
        return aliasNodeMap;
    }

    /**
     * 多别名键解析
     * @param nodeMap           拥有别名的节点Map
     * @param ownerAliasKeys    拥有别名的key列表
     * @return  解析后的节点Map
     */
    public static Map<String, YmlAliasNode> parseAliasNodePath(Map<String, Object> nodeMap, String... ownerAliasKeys) {
        return parseAliasNodePath(nodeMap, null, ownerAliasKeys);
    }

    /**
     * 多别名键解析
     * @param nodeMap           拥有别名的节点Map
     * @param replaceData       如果值存在，使用替换的源强制替换配置的字段
     * @param ownerAliasKeys    拥有别名的key列表
     * @return  解析后的节点Map
     */
    public static Map<String, YmlAliasNode> parseAliasNodePath(Map<String, Object> nodeMap, Object replaceData, String... ownerAliasKeys) {
        Map<String, YmlAliasNode> aliasNodeMap = new HashMap<>();
        for (String ownerAliasKey : ownerAliasKeys) {
            parseAliasNodePath(nodeMap, aliasNodeMap, ownerAliasKey, null, replaceData);
        }
        return aliasNodeMap;
    }

    /**
     * 解析某个别名字段
     * @param nodeMap       拥有别名的节点Map
     * @param result        添加替换别名的目标map
     * @param ownerAliasKey 拥有别名的key
     * @param defaultValue  默认的值
     * @param replaceData   如果值存在，使用替换的源强制替换配置的字段
     */
    public static void parseAliasMapPath(Map<String, Object> nodeMap, Map<String, Object> result, String ownerAliasKey, Object defaultValue, Object replaceData) {
        Map<String, YmlAliasNode> aliasNodeMap = new HashMap<>(2);
        parseAliasNodePath(nodeMap, aliasNodeMap, ownerAliasKey, defaultValue, replaceData);
        if (aliasNodeMap.isEmpty()) {
            return;
        }
        YmlAliasNode ymlAliasNode = aliasNodeMap.get(ownerAliasKey);
        result.put(ymlAliasNode.getKey(), ymlAliasNode.getValue());
    }

    /**
     * 解析某个别名字段
     * @param nodeMap       拥有别名的节点Map
     * @param aliasNodeMap  添加替换别名的目标map（包住源key）
     * @param ownerAliasKey 拥有别名的key
     * @param defaultValue  默认的值
     * @param replaceData   如果值存在，使用替换的源强制替换配置的字段
     */
    @SuppressWarnings("all")
    public static void parseAliasNodePath(Map<String, Object> nodeMap, Map<String, YmlAliasNode> aliasNodeMap, String ownerAliasKey, Object defaultValue, Object replaceData) {
        String key = ownerAliasKey;
        Object value = nodeMap.get(ownerAliasKey);
        // 未指定的配置字段，如果有默认值
        if (value == null && defaultValue != null) {
            value = defaultValue;
        } else if (value instanceof Map) { // 别名替换
            Map<String, Object> valueMap = (Map<String, Object>) value;
            key = String.valueOf(valueMap.keySet().toArray()[0]);
            value = valueMap.get(key);
        }
        // 配置中未指定返回的字段，直接返回
        if (value == null) {
            return;
        }

        // 创建别名节点
        YmlAliasNode ymlAliasNode = new YmlAliasNode();
        ymlAliasNode.setKey(key);
        ymlAliasNode.setValue(value);

        // 替换是数据来源
        if (replaceData instanceof Map) {
            Map<String, Object> replaceMap = (Map<String, Object>) replaceData;
            ymlAliasNode.setValue(replaceMap.get(ymlAliasNode.getKey()));
            aliasNodeMap.put(ownerAliasKey, ymlAliasNode);
            return;
        }
        // 替换是指定的值
        if (StringUtils.isEmpty(value) && replaceData != null) {
            ymlAliasNode.setValue(replaceData);
        }
        aliasNodeMap.put(ownerAliasKey,  ymlAliasNode);
    }
}
