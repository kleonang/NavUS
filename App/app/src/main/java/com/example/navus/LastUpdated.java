package com.example.navus;

//class for the structure stored in Firebase to know if the data was updated.
public class LastUpdated {
    String ID;
    String UpdatedDate;

    public LastUpdated() {
    }

    public LastUpdated(String UpdatedDate){
        this.UpdatedDate = UpdatedDate;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getUpdatedDate() {
        return UpdatedDate;
    }

    public void setUpdatedDate(String updatedDate) {
        UpdatedDate = updatedDate;
    }
}
