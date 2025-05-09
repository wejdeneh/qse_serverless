package com.QSE.models;
import java.util.*;
public class CreateSubFilesPayload {
    public String filename;
    public List<String> subgroup;
    public int groupNumber;
    public CreateSubFilesPayload(String filename, List<String> subgroup, int groupNumber) {
        this.filename = filename;
        this.subgroup = subgroup;
        this.groupNumber = groupNumber;
    }
    public String getFilename() {
        return filename;
    }
    public List<String> getSubgroup() {
        return subgroup;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }
    public void setSubgroup(List<String> subgroup) {
        this.subgroup = subgroup;
    }
    public CreateSubFilesPayload() {
    }
    public int getGroupNumber() {
        return groupNumber;
    }
    public void setGroupNumber(int groupNumber) {
        this.groupNumber = groupNumber;
    }
}
