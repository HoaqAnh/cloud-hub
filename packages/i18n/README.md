# CloudHub translations

Edit `locales/en.json` and `locales/vi.json` together. Keep keys at most two levels deep. For example, `explorer.emptyFolder` fits the limit, while `explorer.folder.empty` exceeds it.

Use i18next interpolation, such as `{{count}}`, for dynamic text and plural suffixes such as `_one` and `_other` for counts. Run `pnpm test` from the repository root to check matching keys, key depth, and fallback.
