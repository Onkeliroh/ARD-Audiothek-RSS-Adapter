package cache

import (
	"sync"
	"time"
)

type entry struct {
	value     string
	expiresAt time.Time
}

// RSSFeedCache is a simple in-memory TTL cache for RSS feed strings.
type RSSFeedCache struct {
	ttl     time.Duration
	mu      sync.RWMutex
	entries map[string]entry
}

// New creates a new RSSFeedCache with the given TTL.
func New(ttl time.Duration) *RSSFeedCache {
	return &RSSFeedCache{
		ttl:     ttl,
		entries: make(map[string]entry),
	}
}

// Get returns the cached value for key, or ("", false) if absent or expired.
func (c *RSSFeedCache) Get(key string) (string, bool) {
	c.mu.RLock()
	e, ok := c.entries[key]
	c.mu.RUnlock()
	if !ok {
		return "", false
	}
	if time.Now().After(e.expiresAt) {
		c.mu.Lock()
		delete(c.entries, key)
		c.mu.Unlock()
		return "", false
	}
	return e.value, true
}

// Set stores value under key, expiring after the cache TTL.
func (c *RSSFeedCache) Set(key, value string) {
	c.mu.Lock()
	c.entries[key] = entry{value: value, expiresAt: time.Now().Add(c.ttl)}
	c.mu.Unlock()
}

// GetOrLoad returns the cached value for key, or calls loader to produce it and
// stores the result before returning.
func (c *RSSFeedCache) GetOrLoad(key string, loader func() (string, error)) (string, error) {
	if v, ok := c.Get(key); ok {
		return v, nil
	}
	value, err := loader()
	if err != nil {
		return "", err
	}
	c.Set(key, value)
	return value, nil
}

// Clear removes all entries from the cache.
func (c *RSSFeedCache) Clear() {
	c.mu.Lock()
	c.entries = make(map[string]entry)
	c.mu.Unlock()
}
