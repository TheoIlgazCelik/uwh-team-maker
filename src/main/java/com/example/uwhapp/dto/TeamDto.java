package com.example.uwhapp.dto;

import java.util.List;

public class TeamDto {
    private Integer index;
    private List<Long> memberIds;

    public TeamDto() {}

    public TeamDto(Integer index, List<Long> memberIds) {
        this.index = index;
        this.memberIds = memberIds;
    }

    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public List<Long> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Long> memberIds) { this.memberIds = memberIds; }
}
