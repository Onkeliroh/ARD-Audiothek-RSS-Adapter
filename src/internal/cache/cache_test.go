package cache_test

import (
	"testing"
	"time"

	"github.com/onkeliroh/ard-audiothek-rss-adapter/src/internal/cache"
)

func TestReturnsCachedValueWhileEntryIsFresh(t *testing.T) {
	c := cache.New(60 * time.Second)
	counter := 0
	load := func() (string, error) {
		counter++
		return "value-1", nil
	}

	v1, err := c.GetOrLoad("foo", load)
	if err != nil {
		t.Fatal(err)
	}
	v2, err := c.GetOrLoad("foo", load)
	if err != nil {
		t.Fatal(err)
	}

	if v1 != "value-1" {
		t.Errorf("v1 = %q, want value-1", v1)
	}
	if v2 != "value-1" {
		t.Errorf("v2 = %q, want value-1", v2)
	}
	if counter != 1 {
		t.Errorf("loader called %d times, want 1", counter)
	}
}

func TestRefreshesEntryAfterTTLExpires(t *testing.T) {
	c := cache.New(50 * time.Millisecond)
	counter := 0

	v1, _ := c.GetOrLoad("foo", func() (string, error) {
		counter++
		return "value-1", nil
	})

	time.Sleep(60 * time.Millisecond)

	v2, _ := c.GetOrLoad("foo", func() (string, error) {
		counter++
		return "value-2", nil
	})

	if v1 == v2 {
		t.Errorf("expected different values after TTL, got %q both times", v1)
	}
	if counter != 2 {
		t.Errorf("loader called %d times, want 2", counter)
	}
}

func TestClearRemovesAllEntries(t *testing.T) {
	c := cache.New(60 * time.Second)
	c.Set("key1", "value1")
	c.Set("key2", "value2")

	c.Clear()

	if _, ok := c.Get("key1"); ok {
		t.Error("expected key1 to be absent after Clear")
	}
	if _, ok := c.Get("key2"); ok {
		t.Error("expected key2 to be absent after Clear")
	}
}
