package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    static final String FAVOURITES = "favourites";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAV_FILE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp|json|psd)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        if (segments.size() == 2 && FAVOURITES.equals(segments.get(0)) && FAV_FILE.matcher(segments.get(1)).matches()) {
            return FAVOURITES + "/" + segments.get(1);
        }
        if (segments.size() == 3 && FAVOURITES.equals(segments.get(0))
                && ("scenes".equals(segments.get(1)) || "posters".equals(segments.get(1)))
                && FAV_FILE.matcher(segments.get(2)).matches()) {
            return FAVOURITES + "/" + segments.get(1) + "/" + segments.get(2);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    /** True for "favourites" alone: a delete of it clears the favourites assets. */
    static boolean isFavourites(List<String> segments) {
        return segments != null && segments.size() == 1 && FAVOURITES.equals(segments.get(0));
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
