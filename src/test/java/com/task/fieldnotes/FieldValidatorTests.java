package com.task.fieldnotes;

import com.task.fieldnotes.data.FieldValidator;
import com.task.fieldnotes.common.ApiException;
import com.task.fieldnotes.sync.SyncModels.EntityType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FieldValidatorTests {
    private final FieldValidator validator=new FieldValidator();
    private Map<String,Object> note() {
        return new HashMap<>(Map.of("siteId",UUID.randomUUID().toString(),"title","Inspection","occurredAt","2026-10-08T14:00:00+06:00","status","OPEN"));
    }
    @Test void canonicalizesDatesAndOptionalFields() {
        var result=validator.validate(EntityType.NOTE,note());
        assertThat(result.get("occurredAt")).isEqualTo("2026-10-08T08:00:00Z");
        assertThat(result.get("description")).isEqualTo("");
        assertThat(result).containsEntry("latitude",null).containsEntry("longitude",null).containsEntry("photoId",null);
    }
    @Test void rejectsPartialOrNonFiniteCoordinates() {
        var input=note(); input.put("latitude",20);
        assertThatThrownBy(()->validator.validate(EntityType.NOTE,input)).isInstanceOf(ApiException.class);
        input.put("longitude",Double.NaN);
        assertThatThrownBy(()->validator.validate(EntityType.NOTE,input)).isInstanceOf(ApiException.class);
        input.put("longitude",180); input.put("latitude",-90);
        assertThat(validator.validate(EntityType.NOTE,input)).containsEntry("longitude",180.0).containsEntry("latitude",-90.0);
    }
    @Test void rejectsInvalidStatusTimestampAndUnknownFields() {
        var input=note(); input.put("status","UNKNOWN");
        assertThatThrownBy(()->validator.validate(EntityType.NOTE,input)).isInstanceOf(ApiException.class);
        input.put("status","DRAFT"); input.put("occurredAt","2026-10-08T14:00:00");
        assertThatThrownBy(()->validator.validate(EntityType.NOTE,input)).isInstanceOf(ApiException.class);
        input.put("ownerId",UUID.randomUUID().toString());
        assertThatThrownBy(()->validator.validate(EntityType.NOTE,input)).isInstanceOf(ApiException.class);
    }
}
