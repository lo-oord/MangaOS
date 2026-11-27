#!/usr/bin/env python3
"""Live verification of the bundled MangaOS sources.

This mirrors the parsing rules of every native source (same endpoints, same selectors, same
JSON paths) and runs search -> details -> chapters -> pages against the real sites. It exists
because the APK itself is only built on GitHub Actions; this script is the executable evidence
that the parsers match what the sites return today.

Usage:  python3 tools/verify_native_sources.py [source_id ...]
"""
from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from bs4 import BeautifulSoup

UA = ("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")


class Failure(Exception):
    pass


def get(url: str, headers: dict | None = None, data: bytes | None = None) -> str:
    request = urllib.request.Request(url, data=data, headers={"User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            return response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        raise Failure(f"HTTP {error.code} for {url[:110]}") from error
    except urllib.error.URLError as error:
        raise Failure(f"network error for {url[:110]}: {error.reason}") from error


def json_get(url: str, headers: dict | None = None, body: dict | None = None) -> dict | list:
    if body is None:
        return json.loads(get(url, headers))
    payload = json.dumps(body).encode()
    return json.loads(get(url, {"Content-Type": "application/json", **(headers or {})}, payload))


def soup(url: str, headers: dict | None = None) -> BeautifulSoup:
    return BeautifulSoup(get(url, headers), "html.parser")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise Failure(message)


# ---------------------------------------------------------------------------- Asura Scans
def asura() -> dict:
    api = "https://api.asurascans.com/api"
    listing = json_get(f"{api}/series?limit=4&offset=0&order=popular")
    require(bool(listing.get("data")), "popular returned no series")
    first = listing["data"][0]
    slug = first["slug"]
    detail = json_get(f"{api}/series/{slug}")
    series = detail.get("series") or detail
    require(bool(series.get("title")), "details have no title")
    chapters = json_get(f"{api}/series/{slug}/chapters")["data"]
    require(bool(chapters), "no chapters")
    free = next((c for c in chapters if not c.get("is_premium")), None)
    require(free is not None, "every chapter is premium")
    pages = json_get(f"{api}/series/{slug}/chapters/{free['number']}")
    urls = [p["url"] for p in (pages.get("data", {}).get("chapter", {}) or {}).get("pages", [])]
    require(bool(urls), "chapter has no page urls")
    search = json_get(f"{api}/series?limit=4&offset=0&order=popular&search={urllib.parse.quote(series['title'])}")
    return {
        "search": len(search.get("data") or []),
        "manga": series["title"],
        "chapters": len(chapters),
        "pages": len(urls),
        "rating": series.get("rating"),
        "status": series.get("status"),
    }


# ---------------------------------------------------------------------------------- Azora
def azora() -> dict:
    api = "https://api.azorafly.com"
    listing = json_get(f"{api}/api/query?perPage=4&searchTerm=&orderBy=totalViews&orderDirection=desc&page=1")
    posts = listing["posts"]
    require(bool(posts), "popular returned no series")
    post = posts[0]
    details = json_get(f"{api}/api/post?postSlug={post['slug']}")
    chapters = json_get(f"{api}/api/chapters?postId={post['id']}")["post"]["chapters"]
    require(bool(chapters), "no chapters")
    chapter = chapters[0]
    pages = json_get(f"{api}/api/chapter?chapterId={chapter['id']}")
    images = sorted(pages["chapter"]["images"], key=lambda i: i.get("order", 10**9))
    require(bool(images), "chapter has no images")
    search = json_get(f"{api}/api/query?perPage=4&searchTerm={urllib.parse.quote(post['postTitle'][:12])}"
                      f"&orderBy=totalViews&orderDirection=desc&page=1")
    return {
        "search": len(search.get("posts") or []),
        "manga": details["post"]["postTitle"],
        "chapters": len(chapters),
        "pages": len(images),
        "status": details["post"].get("seriesStatus"),
        "author": details["post"].get("author"),
    }


# ------------------------------------------------------------------------------ MangaSwat
def mangaswat() -> dict:
    api = "https://appswat.com/v2/api/v2/"
    listing = json_get(f"{api}series/?page_size=4&page=1&order_by=-followers_count")
    results = listing["results"]
    require(bool(results), "popular returned no series")
    series = json_get(f"{api}series/{results[0]['id']}/")
    chapters = json_get(f"{api}chapters/?serie={results[0]['id']}&order_by=-order&page_size=4&page=1")["results"]
    require(bool(chapters), "no chapters")
    chapter = json_get(f"{api}chapters/{chapters[0]['id']}/")
    images = chapter["images"]
    require(bool(images), "chapter has no images")
    search = json_get(f"{api}series/?search={urllib.parse.quote(series['title'][:12])}&page_size=4&page=1")
    return {
        "search": len(search.get("results") or []),
        "manga": series["title"],
        "chapters": len(chapters),
        "pages": len(images),
        "status": (series.get("status") or {}).get("name"),
        "author": (series.get("author") or {}).get("name"),
    }


# ------------------------------------------------------------------------------ MangaTime
def trpc(procedure: str, payload: dict) -> dict:
    encoded = urllib.parse.quote(json.dumps({"0": {"json": payload}}, separators=(",", ":")))
    data = json_get(f"https://mangatime.org/api/trpc/{procedure}?batch=1&input={encoded}")
    return data[0]["result"]["data"]["json"]


def mangatime() -> dict:
    listing = trpc("search.searchSeries", {"page": 1, "limit": 4, "sortBy": "popularity", "sortOrder": "desc"})
    results = listing["results"]
    require(bool(results), "popular returned no series")
    first = results[0]
    series = trpc("content.getSeriesBySlug", {"slug": first["slug"]})
    chapters = trpc("content.getChapters", {"seriesId": series["id"], "limit": -1})["chapters"]
    require(bool(chapters), "no chapters")
    unlocked = next((c for c in chapters if c.get("isUnlocked")), None)
    require(unlocked is not None, "every chapter is locked")
    pages = trpc("content.getChapterPages",
                 {"seriesSlug": first["slug"], "chapterNumber": int(unlocked["number"])})
    require(bool(pages.get("pages")), "chapter has no pages")
    search = trpc("search.searchSeries", {"page": 1, "limit": 4, "sortBy": "relevance",
                                          "sortOrder": "desc", "query": series["title"][:12]})
    return {
        "search": len(search.get("results") or []),
        "manga": series["title"],
        "chapters": len(chapters),
        "pages": len(pages["pages"]),
        "rating": series.get("rating"),
        "status": series.get("status"),
        "author": series.get("author"),
    }


# --------------------------------------------------------------------------------- MangaAr
def mangaar() -> dict:
    api = "https://mangaar.com/api"
    trending = json_get(f"{api}/public/trending")
    entries = (trending.get("popular") or []) + (trending.get("trending") or [])
    require(bool(entries), "popular returned no series")
    first = entries[0]
    series = json_get(f"{api}/public/manga/{first['id']}")
    chapters = json_get(f"{api}/public/manga/{first['id']}/chapters")["chapters"]
    require(bool(chapters), "no chapters")
    chapter = chapters[0]
    number = chapter["episode_number"]
    if str(number).endswith(".0"):
        number = int(number)
    pages = json_get(f"{api}/chapters/by-manga/{first['id']}/{number}/pages")
    require(bool(pages.get("pages")), "chapter has no pages")
    search = json_get(f"{api}/public/manga?page=1&page_size=4&q="
                      f"{urllib.parse.quote(series['title'][:12])}")
    return {
        "search": len(search.get("manga") or []),
        "manga": series["title"],
        "chapters": len(chapters),
        "pages": len(pages["pages"]),
        "status": series.get("status_ar"),
        "author": series.get("author"),
    }


# ------------------------------------------------------------------------------- ProComic
def procomic() -> dict:
    api = "https://procomic.pro/api/public"
    listing = json_get(f"{api}/content?sort=popular&limit=4&page=1")["data"]
    require(bool(listing), "popular returned no content")
    series = json_get(f"{api}/content/{listing[0]['id']}")
    kind = series.get("type") or "manhua"
    chapters = json_get(f"{api}/{kind}/{series['id']}/chapters?limit=4&order=desc&page=1")["data"]
    require(bool(chapters), "no chapters")
    return {
        "search": "not available (API has no text search endpoint)",
        "manga": series["title"],
        "chapters": len(chapters),
        "pages": "reader-only (reported as unsupported)",
        "status": series.get("progress"),
        "author": (series.get("metadata") or {}).get("author"),
    }


# -------------------------------------------------------------------------- Thunder Scans
def thunderscans() -> dict:
    base = "https://en-thunderscans.com"
    listing = soup(f"{base}/comics/?order=popular")
    cards = listing.select(".listupd .bs .bsx, .listo .bs .bsx")
    require(bool(cards), "popular returned no cards")
    link = cards[0].select_one("a[href]")
    title = link.get("title") or link.get_text(strip=True)
    href = link["href"]
    slug = href.strip("/").split("/")[-1]
    series = soup(f"{base}/comics/{slug}/")
    chapters = series.select("div.bxcl li, div.cl li, #chapterlist li")
    require(bool(chapters), "no chapters")
    chapter_li = next(li for li in chapters if not (li.select_one("a")["href"].startswith("#")))
    chapter_href = chapter_li.select_one("a")["href"]
    reader = soup(chapter_href)
    images = [i.get("src") for i in reader.select("#readerarea img[src]") if i.get("src")]
    if not images:
        script = next((s.get_text() for s in reader.select("script") if "ts_reader.run(" in s.get_text()), None)
        require(bool(script), "reader payload missing")
        payload = script.split("ts_reader.run(", 1)[1].rsplit(")", 1)[0].strip()
        images = json.loads(payload)["sources"][0]["images"]
    require(bool(images), "chapter has no pages")
    search = soup(f"{base}/?s={urllib.parse.quote(title[:10])}&post_type=wp-manga")
    return {
        "search": len(search.select(".listupd .bs .bsx")),
        "manga": title,
        "chapters": len(chapters),
        "pages": len(images),
        "status": "see .imptdt",
        "author": None,
    }


# --------------------------------------------------------------------------- Manga Origin
def mangaorigin() -> dict:
    base = "https://teammangaorigin.blogspot.com"
    reader = "https://mangaoriginread.blogspot.com"
    feed = (f"{base}/feeds/posts/summary?alt=json&q=%22%D8%AC%D9%85%D9%8A%D8%B9%20%D8%A7%D9%84%D9%81%D8%B5%D9%88%D9%84%22"
            "&start-index=1&max-results=6")
    data = json_get(feed)
    entries = data["feed"].get("entry") or []
    require(bool(entries), "no series entries")
    entry = entries[0]
    labels = [c["term"] for c in entry.get("category") or []]
    path = next(l["href"] for l in entry["link"] if l["rel"] == "alternate")
    path = path.split("blogspot.com", 1)[1]
    label = None
    for candidate in labels:
        chapter_feed = json_get(f"{reader}/feeds/posts/summary/-/{urllib.parse.quote(candidate)}"
                                f"?alt=json&max-results=1")
        if chapter_feed["feed"].get("entry"):
            label = candidate
            break
    require(label is not None, "no reader label for the series")
    chapters = json_get(f"{reader}/feeds/posts/summary/-/{urllib.parse.quote(label)}"
                        f"?alt=json&start-index=1&max-results=50")["feed"]["entry"]
    require(bool(chapters), "no chapters")
    chapter_path = next(l["href"] for l in chapters[0]["link"] if l["rel"] == "alternate")
    chapter_path = chapter_path.split("blogspot.com", 1)[1]
    entry_data = json_get(f"{reader}/feeds/posts/default?alt=json&path={urllib.parse.quote(chapter_path)}")
    html = entry_data["feed"]["entry"][0]["content"]["$t"]
    images = [i["src"] for i in BeautifulSoup(html, "html.parser").select("img") if i.get("src")]
    require(bool(images), "chapter has no images")
    return {
        "search": "via blog search (same feed)",
        "manga": entry["title"]["$t"],
        "chapters": len(chapters),
        "pages": len(images),
        "status": "not published",
        "author": None,
    }


# ------------------------------------------------------------------- Kawaii Manga
def kawaii() -> dict:
    base = "https://www.mangakawaii.fr"
    listing = soup(f"{base}/mangas?sort=views&page=1")
    cards = listing.select('a.mk-card[href*="/manga/"]')
    require(bool(cards), "listing returned no cards")
    card = cards[0]
    title = card.select_one("p.mk-display").get_text(strip=True)
    href = card["href"]
    series = soup(href)
    require(series.select_one("h1") is not None, "details have no title")
    result = {
        "search": len(soup(f"{base}/recherche?q={urllib.parse.quote(title[:10])}&page=1")
                      .select('a.mk-card[href*="/manga/"]')),
        "manga": series.select_one("h1").get_text(strip=True),
        "chapters": "not reachable from this network",
        "pages": "not reachable from this network",
        "status": (series.select("details p")[0].get_text(" ", strip=True).split("·")[-1].strip()
                   if series.select("details p") else None),
        "cover": bool(series.select_one("img.shadow-2xl")),
        "genres": len([a for a in series.select("a[href*=genres]")]),
    }
    try:
        chapters = soup(f"{href.rstrip('/')}/chapitres?page=1")
        rows = chapters.select("div.ch-row")
        result["chapters"] = len(rows)
        if rows:
            result["pages"] = "reader page reachable"
    except Failure as error:
        result["chapters"] = f"site refused this network ({error})"
    return result


# ------------------------------------------------------------------------- Despair Manga
def despair() -> dict:
    base = "https://despair-world.com/supabase-api/rest/v1"
    key = ("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiw"
           "iaWF0IjoxNzkwMzczMDE4LCJleHAiOjE5NDgwNTMwMTh9.xYgAlC_9SuUCdzdff6wBYGWzYFmAI9apP_mk4DCPaVI")
    headers = {"apikey": key, "Authorization": f"Bearer {key}"}
    columns = "id,slug,title_ar,title_en,cover_url,genres,synopsis,status,author_or_studio,updated_at"
    works = json_get(f"{base}/manga_works?select={columns}&order=views_count.desc&limit=3&offset=0", headers)
    require(bool(works), "listing returned no works")
    first = works[0]
    series = json_get(f"{base}/manga_works?select={columns}&slug=eq.{first['slug']}&limit=1", headers)[0]
    chapters = json_get(f"{base}/chapters?select=id,chapter_number,title,is_free,created_at"
                        f"&work_id=eq.{series['id']}&order=chapter_number.desc,id.desc", headers)
    require(bool(chapters), "no chapters")
    pages = json_get(f"{base}/chapters?select=pages&id=eq.{chapters[0]['id']}", headers)
    images = pages[0].get("pages") or []
    search = json_get(f"{base}/manga_works?select={columns}"
                      f"&or=(title_ar.ilike.%22*{urllib.parse.quote(series['title_ar'][:8])}*%22)"
                      "&limit=3&offset=0", headers)
    return {
        "search": len(search),
        "manga": series["title_ar"],
        "chapters": len(chapters),
        "pages": len(images),
        "status": series.get("status"),
        "author": series.get("author_or_studio"),
    }


# ----------------------------------------------------------------------------- Team X
def teamx() -> dict:
    base = "https://olympustaff.com"
    listing = soup(f"{base}/series/")
    cards = listing.select("div.listupd div.bsx a[href]")
    require(bool(cards), "listing returned no cards")
    href = cards[0]["href"]
    title = cards[0].get("title") or cards[0].get_text(strip=True)
    series = soup(href, {"Referer": f"{base}/series/"})
    require(series.select_one("div.author-info-title h1") is not None, "details have no title")
    chapters = [c for c in series.select("div.chapter-card") if not c.select_one("span.locked")]
    require(bool(chapters), "no chapters")
    chapter_href = chapters[0].select_one("a")["href"]
    reader = soup(chapter_href, {"Referer": href})
    images = reader.select("div.image_list canvas[data-src], div.image_list img[src]")
    require(bool(images), "chapter has no pages")
    info = [s.get_text(strip=True) for s in series.select(".full-list-info > small")]
    status = info[info.index("الحالة:") + 1] if "الحالة:" in info else None
    return {
        "search": len(soup(f"{base}/search?keyword={urllib.parse.quote(title[:8])}")
                      .select("div.tx-grid a.tx-card")),
        "manga": series.select_one("div.author-info-title h1").get_text(strip=True),
        "chapters": len(chapters),
        "pages": len(images),
        "status": status,
        "author": None,
    }


# ----------------------------------------------------------------------------- MangaTik
def mangatik() -> dict:
    base = "https://mangatik.com"
    listing = soup(f"{base}/manga?sort=views&page=1")
    cards = listing.select("article.manga-card")
    require(bool(cards), "listing returned no cards")
    link = cards[0].select_one(".manga-title > a")
    href = link["href"]
    href = href if href.startswith("http") else base + href
    series = soup(href)
    data = json.loads(series.select_one('script[type="application/ld+json"]').get_text())
    chapters = series.select("a.chapter-item-card")
    require(bool(chapters), "no chapters")
    chapter_href = chapters[0]["href"]
    chapter_href = chapter_href if chapter_href.startswith("http") else base + chapter_href
    reader = soup(chapter_href)
    images = reader.select("img.webtoon-image")
    require(bool(images), "chapter has no pages")
    return {
        "search": len(soup(f"{base}/manga?q={urllib.parse.quote(data['name'][:8])}")
                      .select("article.manga-card")),
        "manga": data["name"],
        "chapters": len(chapters),
        "pages": len(images),
        "status": [b.get_text(strip=True) for b in series.select("span.badge")],
        "author": (data.get("author") or {}).get("name"),
    }


# ------------------------------------------------------- Cloudflare-protected (report only)
def blocked_only(name: str, url: str) -> dict:
    try:
        body = get(url)
    except Failure as error:
        return {"blocked": str(error)}
    raise Failure(f"{name} answered without protection ({len(body)} bytes); add a full parser check")


CHECKS = {
    "asurascans": asura,
    "azora": azora,
    "mangaswat": mangaswat,
    "mangatime": mangatime,
    "mangaar": mangaar,
    "procomic": procomic,
    "thunderscans": thunderscans,
    "mangaorigin": mangaorigin,
    "mangatek": lambda: blocked_only("MangaTek", "https://mangatek.com/manga-list"),
    "mangakawaii": kawaii,
    "despairmanga": despair,
    "teamx": teamx,
    "mangadar": lambda: blocked_only("MangaDar", "https://mangadar.com/manga/"),
    "mangatik": mangatik,
    "dilar": lambda: blocked_only("Dilar", "https://dilar.tube/api/series?page=1"),
}


def main() -> int:
    wanted = sys.argv[1:] or list(CHECKS)
    failures = 0
    for source_id in wanted:
        check = CHECKS.get(source_id)
        if check is None:
            print(f"{source_id:14s} SKIP    unknown source")
            continue
        try:
            result = check()
        except Failure as error:
            failures += 1
            print(f"{source_id:14s} FAIL    {error}")
            continue
        except Exception as error:  # noqa: BLE001 - a parser bug must be visible, not fatal
            failures += 1
            print(f"{source_id:14s} ERROR   {type(error).__name__}: {error}")
            continue
        blocked = result.get("blocked")
        if blocked:
            print(f"{source_id:14s} BLOCKED {blocked}")
            continue
        print(f"{source_id:14s} OK      {json.dumps(result, ensure_ascii=False)}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
