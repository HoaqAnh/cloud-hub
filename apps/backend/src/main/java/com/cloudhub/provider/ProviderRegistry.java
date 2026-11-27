package com.cloudhub.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistry {
    private final Map<ProviderType, CloudProvider> providers = new EnumMap<>(ProviderType.class);

    public ProviderRegistry(List<CloudProvider> adapters) {
        for (CloudProvider adapter : adapters) {
            if (providers.putIfAbsent(adapter.type(), adapter) != null) {
                throw new IllegalStateException("Duplicate adapter for " + adapter.type());
            }
        }
    }

    public CloudProvider get(ProviderType type) {
        CloudProvider provider = providers.get(type);
        if (provider == null) throw new IllegalStateException("Unsupported cloud provider");
        return provider;
    }
}
