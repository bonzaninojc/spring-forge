package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuração de cache para uma entidade.
 *
 * Exemplo no forge.json:
 * {
 *   "cache": {
 *     "enabled": true,
 *     "ttlSeconds": 600,
 *     "maxSize": 1000,
 *     "provider": "redis"
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheDefinition {

    private boolean enabled = true;
    private long ttlSeconds = 300;
    private long maxSize = 500;
    /** "redis" ou "caffeine" */
    private String provider = "redis";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public long getMaxSize() { return maxSize; }
    public void setMaxSize(long maxSize) { this.maxSize = maxSize; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}
