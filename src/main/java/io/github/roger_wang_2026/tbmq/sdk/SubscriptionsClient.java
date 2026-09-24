/**
 * Copyright 2026 Roger Wang
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.roger_wang_2026.tbmq.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.roger_wang_2026.tbmq.sdk.model.ClientSubscription;
import io.github.roger_wang_2026.tbmq.sdk.model.ClientSubscriptions;
import io.github.roger_wang_2026.tbmq.sdk.model.TbmqPage;
import io.github.roger_wang_2026.tbmq.sdk.model.TopicSubscription;

import java.util.Set;

public final class SubscriptionsClient extends AbstractTbmqApiClient {
    private static final String PATH = "/api/subscription";
    SubscriptionsClient(TbmqClient client) { super(client); }

    public ClientSubscriptions update(ClientSubscriptions subscriptions) {
        return client.post(PATH, subscriptions, ClientSubscriptions.class);
    }
    public Set<TopicSubscription> getForClient(String clientId) {
        return client.execute(TbmqApiRequest.builder(TbmqHttpMethod.GET, PATH)
                .query("clientId", clientId).build(), new TypeReference<Set<TopicSubscription>>() { }).getBody();
    }
    public TbmqPage<ClientSubscription> list(int pageSize, int page, String textSearch) {
        return client.execute(page(PATH + "/all", pageSize, page, textSearch).build(),
                new TypeReference<TbmqPage<ClientSubscription>>() { }).getBody();
    }
    public void clearEmptyTopicTrieNodes() { client.delete(PATH + "/topic-trie/clear"); }
}
