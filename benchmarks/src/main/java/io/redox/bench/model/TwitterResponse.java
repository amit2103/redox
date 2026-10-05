package io.redox.bench.model;

import io.redox.annotation.DoxSerializable;
import java.util.List;

@DoxSerializable
public class TwitterResponse {
    public List<Tweet>         statuses;
    public SearchMetadata      search_metadata;

    @DoxSerializable
    public static class Tweet {
        public long        id;
        public String      id_str;
        public String      text;
        public String      created_at;
        public String      source;
        public boolean     truncated;
        public boolean     favorited;
        public boolean     retweeted;
        public int         retweet_count;
        public int         favorite_count;
        public User        user;
        public Entities    entities;
        public String      lang;
    }

    @DoxSerializable
    public static class User {
        public long    id;
        public String  id_str;
        public String  name;
        public String  screen_name;
        public String  location;
        public String  description;
        public boolean verified;
        public int     followers_count;
        public int     friends_count;
        public int     listed_count;
        public int     favourites_count;
        public int     statuses_count;
        public String  created_at;
        public String  lang;
    }

    @DoxSerializable
    public static class Entities {
        public List<Hashtag>     hashtags;
        public List<Url>         urls;
        public List<UserMention> user_mentions;
    }

    @DoxSerializable
    public static class Hashtag {
        public String        text;
        public List<Integer> indices;
    }

    @DoxSerializable
    public static class Url {
        public String        url;
        public String        expanded_url;
        public String        display_url;
        public List<Integer> indices;
    }

    @DoxSerializable
    public static class UserMention {
        public String        screen_name;
        public String        name;
        public long          id;
        public String        id_str;
        public List<Integer> indices;
    }

    @DoxSerializable
    public static class SearchMetadata {
        public int    count;
        public String completed_in;
        public long   max_id;
        public String max_id_str;
        public String next_results;
        public String query;
        public String refresh_url;
        public int    since_id;
        public String since_id_str;
    }
}
