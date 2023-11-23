package com.tw.common.seedwork;

import java.util.List;
import java.util.Map;

public record LinksResponse(List<Link> links) {

    public record Link(String rel, String href) {
    }

    public static LinksResponse buildLinksResponse(Map<String, String> map) {
        return new LinksResponse(map.entrySet()
                                    .stream()
                                    .map(entry -> new Link(entry.getKey(), entry.getValue()))
                                    .toList());
    }
}
