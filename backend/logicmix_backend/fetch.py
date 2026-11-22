"""Сетевой слой: timeout, retry с exponential backoff, rate limit, лимит размера."""
from __future__ import annotations

import time
import urllib.request
import urllib.error


class FetchError(Exception):
    pass


class RateLimiter:
    def __init__(self, min_interval: float, clock=time.monotonic, sleep=time.sleep):
        self.min_interval, self._clock, self._sleep, self._last = min_interval, clock, sleep, None

    def wait(self) -> None:
        now = self._clock()
        if self._last is not None and now - self._last < self.min_interval:
            self._sleep(self.min_interval - (now - self._last))
        self._last = self._clock()


def fetch_bytes(url: str, *, timeout: float = 30, retries: int = 3, backoff: float = 1.0,
                max_bytes: int = 512 * 1024 * 1024, limiter: RateLimiter | None = None,
                opener=urllib.request.urlopen, sleep=time.sleep,
                user_agent: str = "LogicMixContentBot/1.0 (contact: owner)") -> bytes:
    last: Exception | None = None
    for attempt in range(retries + 1):
        if limiter:
            limiter.wait()
        try:
            req = urllib.request.Request(url, headers={"User-Agent": user_agent})
            with opener(req, timeout=timeout) as r:
                data = r.read(max_bytes + 1)
            if len(data) > max_bytes:
                raise FetchError(f"response exceeds {max_bytes} bytes")   # не повторяем
            if not data:
                raise FetchError("empty response")
            return data
        except FetchError:
            raise
        except urllib.error.HTTPError as e:
            if 400 <= e.code < 500 and e.code != 429:
                raise FetchError(f"HTTP {e.code}") from e
            last = e
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            last = e
        if attempt < retries:
            sleep(backoff * (2 ** attempt))
    raise FetchError(f"failed after {retries + 1} attempts: {last}")
