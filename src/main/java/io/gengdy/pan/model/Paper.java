package io.gengdy.pan.model;

public class Paper
{
    private final String id;
    private final String title;
    private final String authors;
    private final String abstractText;
    private final String url;
    private String aiSummary;

    public Paper(String id, String title, String authors,
                 String abstractText, String url)
    {
        this.id = id;
        this.title = title;
        this.authors = authors;
        this.abstractText = abstractText;
        this.url = url;
    }

    public String getId()
    {
        return id;
    }

    public String getTitle()
    {
        return title;
    }

    public String getAuthors()
    {
        return authors;
    }

    public String getAbstractText()
    {
        return abstractText;
    }

    public String getUrl()
    {
        return url;
    }

    public String getAiSummary()
    {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary)
    {
        this.aiSummary = aiSummary;
    }

    @Override
    public String toString()
    {
        return "Paper{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", authors='" + authors + '\'' +
                ", abstractText='" + abstractText + '\'' +
                ", url='" + url + '\'' +
                ", aiSummary='" + aiSummary + '\'' +
                '}';
    }
}
