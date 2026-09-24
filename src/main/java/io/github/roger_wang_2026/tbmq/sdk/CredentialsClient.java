/**
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.roger_wang_2026.tbmq.sdk.model.MqttClientCredentials;
import io.github.roger_wang_2026.tbmq.sdk.model.TbmqPage;

import java.util.Map;

public final class CredentialsClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/mqtt/client/credentials";
    CredentialsClient(TbmqClient client) { super(client); }

    public MqttClientCredentials save(MqttClientCredentials credentials) {
        return client.post(PATH, credentials, MqttClientCredentials.class);
    }
    public MqttClientCredentials get(String id) { return client.get(PATH + "/" + segment(id), MqttClientCredentials.class); }
    public MqttClientCredentials getByName(String name) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH).query("name", name).build(),
                MqttClientCredentials.class).getBody();
    }
    public TbmqPage<MqttClientCredentials> list(int pageSize, int page, String textSearch) {
        return client.execute(page(PATH, pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<MqttClientCredentials>>() { }).getBody();
    }
    public MqttClientCredentials changePassword(String id, String currentPassword, String newPassword) {
        Map<String, String> body = new java.util.LinkedHashMap<String, String>();
        body.put("currentPassword", currentPassword); body.put("newPassword", newPassword);
        return client.post(PATH + "/" + segment(id), body, MqttClientCredentials.class);
    }
    public Map<String, Long> stats() {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH + "/info").build(),
                new TypeReference<Map<String, Long>>() { }).getBody();
    }
    public void delete(String id) { client.delete(PATH + "/" + segment(id)); }
}
