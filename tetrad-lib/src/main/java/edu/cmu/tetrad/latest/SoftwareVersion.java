package edu.cmu.tetrad.latest;

import java.util.Date;


/**
 * Author : Jeremy Espino MD
 * Created  8/29/16 11:53 AM
 *
 * ADT for describing software version objects
 *
 */

public class SoftwareVersion {

    private long id;
    private String softwareName;
    private String softwareVersion;
    private Date startDate;

    public SoftwareVersion() {
    }

    public SoftwareVersion(String softwareVersion, String softwareName, Date startDate) {
        this.softwareVersion = softwareVersion;
        this.softwareName = softwareName;
        this.startDate = startDate;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSoftwareName() {
        return softwareName;
    }

    public void setSoftwareName(String softwareName) {
        this.softwareName = softwareName;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }
}

