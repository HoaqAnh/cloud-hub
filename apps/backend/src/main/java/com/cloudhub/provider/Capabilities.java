package com.cloudhub.provider;

public record Capabilities(
    boolean pagination,
    boolean search,
    boolean quota,
    boolean download,
    boolean upload
) {}
