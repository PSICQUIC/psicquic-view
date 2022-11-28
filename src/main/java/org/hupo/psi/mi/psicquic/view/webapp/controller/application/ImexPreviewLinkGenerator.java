package org.hupo.psi.mi.psicquic.view.webapp.controller.application;

import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Controller("imexPreview")
public class ImexPreviewLinkGenerator {

    private static final String PROPERTIES = "/org/hupo/psi/mi/psicquic/binarysearch/imex-preview.properties";
    private String imexPreviewUrl;

    private String imexId;


    @PostConstruct
    private void init() {
        Properties props = new Properties();
        try {
            props.load(ImexPreviewLinkGenerator.class.getResourceAsStream(PROPERTIES));
        } catch (IOException e) {
            throw new RuntimeException("Problems reading imex preview file from resource: " + PROPERTIES);
        }

        imexPreviewUrl = props.getProperty("imex.preview.url");
    }

    public URI generateURI() {
        return URI.create(generateURL());
    }

    public String generateURL() {
        return imexPreviewUrl.replaceAll("\\{0\\}", imexId);
    }

    public enum Format {
        html("HTML", true),
        xml254_compact("miXML <sub>2.5</sub> <i>(compact)</i>", false),
        xml300_compact("miXML <sub>3.0</sub> <i>(compact)</i>", false),
        xml254_expanded("miXML <sub>2.5</sub> <i>(expanded)</i>", true),
        xml300_expanded("miXML <sub>3.0</sub> <i>(expanded)</i>", true),
        tab25("miTab <sub>2.5</sub>", true),
        tab26("miTab <sub>2.6</sub>", true),
        tab27("miTab <sub>2.7</sub>", true),
        json("miJSON <sub>1.0</sub>", true),
        json_binary("miJSON <sub>1.0</sub> <i>(binary)</i>"),
        sda("SDA");

        public final String htmlRender;
        public final boolean visible;
        private String url;

        Format(String htmlRender) {
            this.htmlRender = htmlRender;
            this.visible = false;
        }

        Format(String htmlRender, boolean visible) {
            this.htmlRender = htmlRender;
            this.visible = visible;
        }

        public boolean isVisible() {
            return visible;
        }

        public String getHtmlRender() {
            return htmlRender;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public static List<Format> visible() {
            List<Format> list = new ArrayList<Format>();
            for (Format format : Format.values()) {
                if (format.isVisible()) {
                    list.add(format);
                }
            }
            return list;
        }
    }

    public List<Format> getFormats() {
        List<Format> visible = Format.visible();
        for (Format format : visible) {
            format.setUrl(generateURL());
        }
        return visible;
    }

    public String getImexId() {
        return imexId;
    }

    public void setImexId(String imexId) {
        this.imexId = imexId;
    }
}
