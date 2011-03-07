package org.example.android.api;

import java.util.ArrayList;

import org.example.android.api.entity.MixiPerson;

/** People API のレスポンス */
public class PeopleApiResponse {
    public ArrayList<MixiPerson> entry;
    public int totalResults;
    public int startIndex;
    public int itemsPerPage;
}