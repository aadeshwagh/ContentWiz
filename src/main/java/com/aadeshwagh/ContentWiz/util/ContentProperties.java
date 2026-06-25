package com.aadeshwagh.ContentWiz.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "content")
public class ContentProperties {

    private Map<String, TypeConfig> types = new HashMap<>();

    public Map<String, TypeConfig> getTypes() { return types; }
    public void setTypes(Map<String, TypeConfig> types) { this.types = types; }

    public static class TypeConfig {
        private InstagramConfig instagram = new InstagramConfig();
        private YouTubeConfig youtube = new YouTubeConfig();

        public InstagramConfig getInstagram() { return instagram; }
        public void setInstagram(InstagramConfig instagram) { this.instagram = instagram; }
        public YouTubeConfig getYoutube() { return youtube; }
        public void setYoutube(YouTubeConfig youtube) { this.youtube = youtube; }
    }

    public static class InstagramConfig {
        private String businessAccountId;
        private String accessToken;

        public String getBusinessAccountId() { return businessAccountId; }
        public void setBusinessAccountId(String v) { this.businessAccountId = v; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String v) { this.accessToken = v; }
    }

    public static class YouTubeConfig {
        private String clientId;
        private String clientSecret;
        private String channelId;
        private String refreshToken;
        private String privacyStatus = "public";   // public | private | unlisted
        private String categoryId    = "22";        // 22 = People & Blogs

        public String getClientId() { return clientId; }
        public void setClientId(String v) { this.clientId = v; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String v) { this.clientSecret = v; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String v) { this.refreshToken = v; }
        public String getPrivacyStatus() { return privacyStatus; }
        public void setPrivacyStatus(String v) { this.privacyStatus = v; }
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String v) { this.categoryId = v; }
        public String getChannelId(){
            return this.channelId;
        }

        public void setChannelId(String channelId) {
            this.channelId = channelId;
        }
    }
}