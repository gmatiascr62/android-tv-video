#!/usr/bin/env python3
"""Refresh video.json with the current C5N live stream's master HLS URL."""

import json
import os
import sys
from pathlib import Path

import yt_dlp

CHANNEL_LIVE_URL = "https://www.youtube.com/@c5n/live"
VIDEO_JSON_PATH = Path(__file__).resolve().parent.parent / "video.json"

# Si existe, se usa para autenticar con YouTube y evitar el bloqueo de
# "Sign in to confirm you're not a bot" que tiran las IPs de datacenter
# (como las de GitHub Actions). Se genera en el workflow a partir del
# secreto YT_COOKIES.
COOKIES_FILE = os.environ.get("YT_COOKIES_FILE")


def get_master_hls_url(url: str) -> str | None:
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        # El cliente "android" no devuelve variantes HLS para este stream
        # (formats vacío). Usamos "web", que sí las trae -- pero ese
        # cliente le exige a veces un PO token extra a las cookies, que
        # resolvemos con el plugin bgutil-ytdlp-pot-provider + su servicio
        # (ver workflow).
        "extractor_args": {"youtube": {"player_client": ["web"]}},
    }
    if COOKIES_FILE and Path(COOKIES_FILE).is_file():
        ydl_opts["cookiefile"] = COOKIES_FILE
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)

    if not info.get("is_live") and info.get("live_status") != "is_live":
        print(f"No está en vivo ahora (live_status={info.get('live_status')}), no se actualiza video.json.")
        return None

    # Cada "format" de yt-dlp es la sub-playlist de una pista individual
    # (una por video, otra por audio). Necesitamos la playlist MAESTRA, que
    # asocia todas las pistas entre sí -- ExoPlayer elige/combina solo,
    # igual que con cualquier otro canal HLS. Está en "manifest_url" de
    # cualquiera de las variantes HLS (es la misma para todas).
    hls_formats = [
        f for f in (info.get("formats") or [])
        if f.get("protocol") in ("m3u8", "m3u8_native")
    ]
    if not hls_formats:
        print("No se encontraron variantes HLS.")
        return None

    return hls_formats[0].get("manifest_url") or hls_formats[0].get("url")


def main():
    try:
        master_url = get_master_hls_url(CHANNEL_LIVE_URL)
    except Exception as e:
        print(f"Error al extraer el stream: {e}")
        sys.exit(0)  # no romper el workflow, simplemente no se actualiza esta vez

    if not master_url:
        sys.exit(0)

    VIDEO_JSON_PATH.write_text(json.dumps({"url": master_url}, indent=2) + "\n")
    print(f"video.json actualizado con:\n{master_url}")


if __name__ == "__main__":
    main()
