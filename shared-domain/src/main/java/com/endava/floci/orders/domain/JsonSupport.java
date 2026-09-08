package com.endava.floci.orders.domain;

import tools.jackson.databind.json.JsonMapper;

public final class JsonSupport {
    private JsonSupport() {}

    public static JsonMapper mapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}
