# ChatFilter

**A high-performance, modular chat moderation plugin for Paper 1.21**

ChatFilter protects your Minecraft server from toxic chat, spam, and advertising using a multi-layered pipeline architecture. Built for performance at scale with zero-compromise detection.

## Features

- **Profanity filtering** — Aho-Corasick (exact) + BK-tree (fuzzy) matching against a curated swear database
- **Evasion resistance** — Decodes leet-speak, confusable Unicode, zero-width characters, and separator tricks
- **Spam prevention** — Flood detection, rate limiting, repeated-char filtering
- **Security filters** — Blocks IP addresses, domain names, and server-name advertising
- **Customizable** — Regex filter, character whitelist, add/remove words & servers at runtime
- **Persistence** — SQLite-backed player stats, violation history, and filter data
- **Performance** — Async Caffeine caching, minimal allocations, shaded FastUtil collections
- **Bypass system** — Granular permission-based bypass per player

## Requirements

- **Paper** 1.21
- **Java** 21+

## Disclaimer

This plugin was developed with the assistance of AI tools. All code has been reviewed and tested for functionality and security.
