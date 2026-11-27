#!/usr/bin/env python3
"""Live check of every native source bundled in MangaOS.

Mirrors the requests the Android sources make, so a source is only reported healthy when the
running app would receive the same list, search result, details, chapter list and page URLs.

Usage:  python3 tools/verify_native_sources.py
Exit code 0 only when every checked operation succeeded.
"""

from __future__ import annotations

import json
import sys
import urllib.parse
import urllib.request

APP_UA = "MangaOS/1.0 (Android)"
WEB_UA = (
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)
TIMEOUT = 30


def fetch(url: str, headers: dict[str, str]) -> str:
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        return response.read().decode("utf-8", "replace")


def fetch_json(url: str, headers: dict[str, str]):
    return json.loads(fetch(url, headers))


def check(name: str, fn) -> bool:
    try:
        detail = fn()
        print(f"  PASS  {name}: {detail}")
        return True
    except Exception as error:  # noqa: BLE001 - the report needs the real error
        print(f"  FAIL  {name}: {type(error).__name__}: {error}")
        return False


MDX = {"User-Agent": APP_UA, "Accept": "application/json"}
WEB = {"User-Agent": WEB_UA, "Referer": "https://www.mangaclub.net/"}


def mangadex(language: str, sample_search: str) -> bool:
    print(f"\nMangaDex [{language}]")
    api = "https://api.mangadex.org"
    ok = True

    def localized(items):
        # Same rule as the app: only works that have a chapter in this source's language.
        return [
            item
            for item in items
            if not item["attributes"]["availableTranslatedLanguages"]
            or language in item["attributes"]["availableTranslatedLanguages"]
        ]

    def listing():
        query = urllib.parse.urlencode(
            [
                ("limit", "24"),
                ("order[followedCount]", "desc"),
                ("contentRating[]", "safe"),
                ("includes[]", "cover_art"),
            ]
        )
        payload = fetch_json(f"{api}/manga?{query}", MDX)
        items = localized(payload["data"])
        titles = [next(iter(item["attributes"]["title"].values())) for item in items]
        assert titles, "empty catalogue"
        return ", ".join(titles[:3])

    def latest():
        query = urllib.parse.urlencode(
            [
                ("limit", "5"),
                ("order[latestUploadedChapter]", "desc"),
                ("contentRating[]", "safe"),
            ]
        )
        payload = fetch_json(f"{api}/manga?{query}", MDX)
        return f"{len(payload['data'])} works"

    def search():
        query = urllib.parse.urlencode([("limit", "5"), ("title", sample_search)])
        payload = fetch_json(f"{api}/manga?{query}", MDX)
        titles = [next(iter(item["attributes"]["title"].values())) for item in payload["data"]]
        assert titles, f"no result for {sample_search!r}"
        return ", ".join(titles[:3])

    state: dict[str, str] = {}

    def feed_of(manga_id: str):
        query = urllib.parse.urlencode(
            [
                ("limit", "500"),
                ("translatedLanguage[]", language),
                ("order[chapter]", "asc"),
                ("contentRating[]", "safe"),
            ]
        )
        return fetch_json(f"{api}/manga/{manga_id}/feed?{query}", MDX)

    def details():
        # Pick a work that really has a readable chapter in this language, so the report
        # proves the whole path (details -> chapters -> pages) and not only the first hit.
        query = urllib.parse.urlencode(
            [("limit", "24"), ("title", sample_search), ("includes[]", "cover_art")]
        )
        items = localized(fetch_json(f"{api}/manga?{query}", MDX)["data"])
        assert items, f"no result with {language} chapters for {sample_search!r}"
        # A search hit may only link chapters hosted elsewhere; fall back to the browse list.
        browse = urllib.parse.urlencode(
            [
                ("limit", "24"),
                ("order[followedCount]", "desc"),
                ("contentRating[]", "safe"),
                ("includes[]", "cover_art"),
            ]
        )
        items += localized(fetch_json(f"{api}/manga?{browse}", MDX)["data"])
        for manga in items:
            feed = feed_of(manga["id"])
            hosted = [
                chapter
                for chapter in feed["data"]
                if chapter["attributes"]["pages"] > 0
                and not chapter["attributes"]["externalUrl"]
            ]
            if hosted:
                state["manga"] = manga["id"]
                state["chapter"] = hosted[0]["id"]
                state["chapter_number"] = hosted[0]["attributes"]["chapter"]
                state["total"] = feed["total"]
                assert manga["attributes"]["title"], "missing title"
                return next(iter(manga["attributes"]["title"].values()))
        raise AssertionError(f"no readable {language} chapter in {sample_search!r} results")

    def chapters():
        feed = feed_of(state["manga"])
        hosted = [
            chapter
            for chapter in feed["data"]
            if chapter["attributes"]["pages"] > 0 and not chapter["attributes"]["externalUrl"]
        ]
        assert hosted, "no hosted chapter in this language"
        state["chapter"] = hosted[0]["id"]
        return f"{feed['total']} chapters, chapter {state['chapter_number']}"

    def pages():
        server = fetch_json(f"{api}/at-home/server/{state['chapter']}", MDX)
        base = server["baseUrl"]
        chapter = server["chapter"]
        assert chapter["data"], "no page files"
        url = f"{base}/data/{chapter['hash']}/{chapter['data'][0]}"
        assert url.startswith("https://")
        state["page"] = url
        return f"{len(chapter['data'])} pages, first: {url[:70]}…"

    def image():
        request = urllib.request.Request(state["page"], headers={"User-Agent": APP_UA})
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            assert response.status == 200
            return f"image {response.headers.get('Content-Type')}"

    for name, fn in (
        ("list", listing),
        ("latest", latest),
        ("search", search),
        ("details", details),
        ("chapters", chapters),
        ("pages", pages),
        ("image", image),
    ):
        ok = check(name, fn) and ok
    return ok


def asura() -> bool:
    print("\nAsura Scans [en]")
    api = "https://api.asurascans.com"
    ok = True
    state: dict[str, object] = {}

    def listing():
        payload = fetch_json(f"{api}/api/series?page=1", WEB)
        titles = [item["title"] for item in payload["data"]]
        assert titles, "empty catalogue"
        return ", ".join(titles[:3])

    def latest():
        payload = fetch_json(f"{api}/api/series?page=1&order=updated", WEB)
        return f"{len(payload['data'])} works, newest {payload['data'][0]['last_chapter_at']}"

    def search():
        payload = fetch_json(f"{api}/api/search?q=berserk", WEB)
        titles = [item["title"] for item in payload["data"]]
        assert titles, "search returned nothing"
        return ", ".join(titles[:3])

    def details():
        payload = fetch_json(f"{api}/api/series/lookism", WEB)
        series = payload["series"]
        assert series["title"]
        state["slug"] = series["slug"]
        return series["title"]

    def chapters():
        payload = fetch_json(f"{api}/api/series/{state['slug']}/chapters", WEB)
        chapters = payload["data"]
        assert chapters, "no chapters"
        state["number"] = str(chapters[0]["number"])
        return f"{len(chapters)} chapters, newest {state['number']}"

    def pages():
        payload = fetch_json(
            f"{api}/api/series/{state['slug']}/chapters/{state['number']}", WEB
        )
        pages = payload["data"]["chapter"]["pages"]
        assert pages, f"no pages (gate={payload['data'].get('access_gate')!r})"
        state["page"] = pages[0]["url"]
        return f"{len(pages)} pages"

    def image():
        request = urllib.request.Request(
            state["page"], headers={"User-Agent": WEB_UA, "Referer": "https://asurascans.com/"}
        )
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            assert response.status == 200
            return f"image {response.headers.get('Content-Type')}"

    for name, fn in (
        ("list", listing),
        ("latest", latest),
        ("search", search),
        ("details", details),
        ("chapters", chapters),
        ("pages", pages),
        ("image", image),
    ):
        ok = check(name, fn) and ok
    return ok


def mangaclub() -> bool:
    print("\nManga Club [en]")
    base = "https://www.mangaclub.net"
    ok = True
    state: dict[str, str] = {}

    def listing():
        html = fetch(f"{base}/en/?page=1", WEB)
        assert "series-card-title" in html, "no cards in listing"
        return f"{html.count('series-card-link')} cards"

    def search():
        html = fetch(f"{base}/en/search?" + urllib.parse.urlencode({"q": "one"}), WEB)
        assert "/en/manga/" in html, "search returned no manga links"
        return "search page has results"

    def details():
        html = fetch(f"{base}/en/manga/365-days-to-the-wedding.html", WEB)
        assert '<h1 class="series-title">' in html, "no series title"
        state["slug"] = "365-days-to-the-wedding"
        return "365 Days to the Wedding"

    def chapters():
        html = fetch(f"{base}/en/manga/{state['slug']}.html", WEB)
        assert "chapter-item" in html, "no chapter list"
        return f"{html.count('chapter-link')} chapter links"

    def pages():
        html = fetch(
            f"{base}/en/{state['slug']}/{state['slug']}-chapter-109.html", WEB
        )
        assert "cdn.mangaclub.net/cdn/" in html, "no page images"
        state["page"] = html
        return "chapter page has CDN images"

    def image():
        import re

        match = re.search(r"https://cdn\.mangaclub\.net/cdn/[^\"' )]+", state["page"])
        assert match, "no absolute image URL"
        url = match.group(0)
        request = urllib.request.Request(url, headers=WEB)
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            assert response.status == 200
            return f"image {response.headers.get('Content-Type')} at {url[:60]}…"

    for name, fn in (
        ("list", listing),
        ("search", search),
        ("details", details),
        ("chapters", chapters),
        ("pages", pages),
        ("image", image),
    ):
        ok = check(name, fn) and ok
    return ok


def main() -> int:
    results = {
        "mangadex-ar": mangadex("ar", "one piece"),
        "mangadex-en": mangadex("en", "one piece"),
        "asura": asura(),
        "mangaclub": mangaclub(),
    }
    print("\nSummary")
    for source, healthy in results.items():
        print(f"  {'PASS' if healthy else 'FAIL'}  {source}")
    return 0 if all(results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
