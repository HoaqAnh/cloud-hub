package com.cloudhub.provider;

import java.util.List;

public record Page<T>(List<T> items, String nextPageToken) {}
