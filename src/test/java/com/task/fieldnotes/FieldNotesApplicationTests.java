package com.task.fieldnotes;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class FieldNotesApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient db;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));
        properties.add("spring.datasource.username",()->System.getenv().getOrDefault("TEST_DB_USERNAME","fieldnotes_test"));
        properties.add("spring.datasource.password",()->System.getenv().getOrDefault("TEST_DB_PASSWORD",""));
    }
    String token,email;
    @BeforeEach void account() throws Exception {
        email=UUID.randomUUID()+"@example.com";
        token=body(mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","LongPassword123!"))))
            .andExpect(status().isCreated()).andReturn()).get("token").toString();
    }
    @Test void swaggerIsPublicAndDescribesBearerAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }
    @Test void authenticationAndRevocation() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","WrongPassword!")))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",email.toUpperCase(Locale.ROOT),"password","LongPassword123!")))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"password","LongPassword123!")))).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/logout").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization",bearer())).andExpect(status().isUnauthorized());
    }
    @Test void jwtTokenRefreshAndRotation() throws Exception {
        var loginResponse = body(mvc.perform(post("/api/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("email",email,"password","LongPassword123!"))))
            .andExpect(status().isOk()).andReturn());
        assertThat(loginResponse.get("accessToken")).isNotNull();
        assertThat(loginResponse.get("refreshToken")).isNotNull();
        assertThat(loginResponse.get("tokenType")).isEqualTo("Bearer");
        String initialRefreshToken = loginResponse.get("refreshToken").toString();

        // Refresh with valid refresh token
        var refreshed = body(mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content(json.writeValueAsString(Map.of("refreshToken",initialRefreshToken))))
            .andExpect(status().isOk()).andReturn());
        String newAccessToken = refreshed.get("accessToken").toString();
        String newRefreshToken = refreshed.get("refreshToken").toString();
        assertThat(newAccessToken).isNotEmpty();
        assertThat(newRefreshToken).isNotEmpty();
        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

        // Access me using new access token
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+newAccessToken)).andExpect(status().isOk());

        // Replaying old refresh token must be rejected (token rotation)
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content(json.writeValueAsString(Map.of("refreshToken",initialRefreshToken))))
            .andExpect(status().isUnauthorized());

        // Invalid refresh token must be rejected
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content(json.writeValueAsString(Map.of("refreshToken","bogus-refresh-token"))))
            .andExpect(status().isUnauthorized());

        // Empty refresh token returns 400
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content(json.writeValueAsString(Map.of("refreshToken",""))))
            .andExpect(status().isBadRequest());

        // Revoking via logout revokes the refresh token
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+newAccessToken)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
            .content(json.writeValueAsString(Map.of("refreshToken",newRefreshToken))))
            .andExpect(status().isUnauthorized());
    }
    @Test void idempotencyVersionsAndNoResurrection() throws Exception {
        String id=uuid(), mutation=uuid(); Map<String,Object> data=Map.of("name","Acme","contactInformation","Contact");
        var first=write("customers",id,mutation,0,data,200);
        var replay=write("customers",id,mutation,0,new LinkedHashMap<>(data),200);
        assertThat(replay).isEqualTo(first);
        write("customers",id,mutation,0,Map.of("name","Different"),409);
        write("customers",id,uuid(),0,data,409);
        write("customers",id,uuid(),1,Map.of("name","Renamed"),200);
        mvc.perform(delete("/api/customers/"+id).header("Authorization",bearer()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("mutationId",uuid(),"expectedVersion",2)))).andExpect(status().isOk());
        write("customers",id,uuid(),3,data,409);
    }
    @Test void isolationAndOwnedRelationships() throws Exception {
        String customer=uuid(); write("customers",customer,uuid(),0,Map.of("name","Private"),200);
        String firstToken=token;
        account();
        mvc.perform(get("/api/customers/"+customer).header("Authorization",bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/customers").header("Authorization",bearer())).andExpect(jsonPath("$.items").isEmpty());
        write("sites",uuid(),uuid(),0,Map.of("siteName","Foreign","address","Somewhere","customerId",customer),404);
        mvc.perform(get("/api/sync/changes").header("Authorization",bearer())).andExpect(jsonPath("$.changes").isEmpty());
        token=firstToken;
        mvc.perform(get("/api/customers/"+customer).header("Authorization",bearer())).andExpect(status().isOk());
    }
    @Test void atomicPushRollsBackAndRetrySucceeds() throws Exception {
        String customer=uuid(), site=uuid(), missing=uuid();
        var first=mutation("CUSTOMER",customer,0,Map.of("name","Batch customer"));
        var invalid=mutation("SITE",site,0,Map.of("siteName","Batch site","address","Address","customerId",missing));
        push(List.of(first,invalid),404);
        mvc.perform(get("/api/customers/"+customer).header("Authorization",bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/sync/changes").header("Authorization",bearer())).andExpect(jsonPath("$.nextCursor").value(0));
        var valid=mutation("SITE",site,0,Map.of("siteName","Batch site","address","Address","customerId",customer));
        push(List.of(first,valid),200);
        mvc.perform(get("/api/sites/"+site).header("Authorization",bearer())).andExpect(jsonPath("$.customerName").value("Batch customer"));
    }
    @Test void cascadeAndPaginatedRestore() throws Exception {
        String[] ids=hierarchy();
        mvc.perform(delete("/api/customers/"+ids[0]).header("Authorization",bearer()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("mutationId",uuid(),"expectedVersion",1)))).andExpect(status().isOk());
        mvc.perform(get("/api/notes/"+ids[2]).header("Authorization",bearer())).andExpect(status().isNotFound());
        long cursor=0; Map<String,Map<String,Object>> restored=new HashMap<>(); int count=0;
        boolean more;
        do {
            var response=body(mvc.perform(get("/api/sync/changes").header("Authorization",bearer()).param("after",Long.toString(cursor)).param("limit","2"))
                .andExpect(status().isOk()).andReturn());
            @SuppressWarnings("unchecked") var changes=(List<Map<String,Object>>)response.get("changes");
            for(var change:changes) { count++; restored.put(change.get("entityId").toString(),cast(change.get("data"))); }
            long next=((Number)response.get("nextCursor")).longValue(); assertThat(next).isGreaterThan(cursor); cursor=next;
            more=(Boolean)response.get("hasMore");
        } while(more);
        assertThat(count).isEqualTo(6);
        assertThat(restored).hasSize(3);
        restored.values().forEach(row -> { assertThat(row.get("deleted")).isEqualTo(true); assertThat(((Number)row.get("version")).longValue()).isEqualTo(2); });
        mvc.perform(get("/api/sync/changes").header("Authorization",bearer()).param("after",Long.toString(cursor))).andExpect(jsonPath("$.changes").isEmpty());
        mvc.perform(get("/api/sync/changes").header("Authorization",bearer()).param("after","999999")).andExpect(status().isBadRequest());
    }
    @Test void joinsSearchAndFilters() throws Exception {
        String[] ids=hierarchy();
        for(String q:List.of("Acme","North","Inspection","crack"))
            mvc.perform(get("/api/notes").header("Authorization",bearer()).param("q",q)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].customerName").value("Acme")).andExpect(jsonPath("$.items[0].siteName").value("North"));
        mvc.perform(get("/api/notes").header("Authorization",bearer()).param("status","COMPLETED")).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/notes").header("Authorization",bearer()).param("siteId",uuid())).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/notes").header("Authorization",bearer()).param("q","%_")).andExpect(jsonPath("$.items").isEmpty());
        write("customers",ids[0],uuid(),1,Map.of("name","Renamed customer"),200);
        mvc.perform(get("/api/notes/"+ids[2]).header("Authorization",bearer())).andExpect(jsonPath("$.customerName").value("Renamed customer"));
    }
    @Test void validationAndForeignIdCannotOverwrite() throws Exception {
        write("customers",uuid(),uuid(),0,Map.of("name"," "),400);
        write("customers",uuid(),uuid(),0,Map.of("name","Valid","ownerId",uuid()),400);
        mvc.perform(get("/api/notes").header("Authorization",bearer()).param("size","0")).andExpect(status().isBadRequest());
        String[] ids=hierarchy(); var data=note(ids[1]); data.put("latitude",91); data.put("longitude",0);
        write("notes",ids[2],uuid(),1,data,400);
        String id=ids[0]; account(); write("customers",id,uuid(),0,Map.of("name","Intruder"),409);
    }
    @Test void photoRetriesOwnershipAndNoteReference() throws Exception {
        String photoId=uuid(); byte[] png={(byte)137,80,78,71,13,10,26,10,0};
        mvc.perform(put("/api/photos/"+photoId).header("Authorization",bearer()).contentType("image/png").content(png)).andExpect(status().isOk());
        mvc.perform(put("/api/photos/"+photoId).header("Authorization",bearer()).contentType("image/png").content(png)).andExpect(status().isOk());
        mvc.perform(get("/api/photos/"+photoId).header("Authorization",bearer())).andExpect(content().bytes(png));
        byte[] other=png.clone(); other[8]=1;
        mvc.perform(put("/api/photos/"+photoId).header("Authorization",bearer()).contentType("image/png").content(other)).andExpect(status().isConflict());
        mvc.perform(put("/api/photos/"+uuid()).header("Authorization",bearer()).contentType("image/png").content(new byte[]{1,2,3})).andExpect(status().isBadRequest());
        String[] ids=hierarchy(); var data=note(ids[1]); data.put("photoId",photoId); write("notes",ids[2],uuid(),1,data,200);
        account(); mvc.perform(get("/api/photos/"+photoId).header("Authorization",bearer())).andExpect(status().isNotFound());
        String[] owned=hierarchy(); var foreign=note(owned[1]); foreign.put("photoId",photoId); write("notes",owned[2],uuid(),1,foreign,404);
    }
    @Test void concurrentEditsHaveExactlyOneWinner() throws Exception {
        String id=uuid(); write("customers",id,uuid(),0,Map.of("name","Initial"),200);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=executor.invokeAll(List.of(
                () -> updateStatus(id,"First"), () -> updateStatus(id,"Second")));
            var statuses=List.of(futures.get(0).get(),futures.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200,409);
        }
        mvc.perform(get("/api/customers/"+id).header("Authorization",bearer())).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/sync/changes").header("Authorization",bearer())).andExpect(jsonPath("$.nextCursor").value(2));
    }
    int updateStatus(String id,String name) throws Exception {
        return mvc.perform(put("/api/customers/"+id).header("Authorization",bearer()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("mutationId",uuid(),"expectedVersion",1,"data",Map.of("name",name)))))
            .andReturn().getResponse().getStatus();
    }
    @Test void missingVersionsAndInvalidSearchReturnValidationErrors() throws Exception {
        mvc.perform(put("/api/customers/"+uuid()).header("Authorization",bearer()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("mutationId",uuid(),"data",Map.of("name","Acme")))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
        mvc.perform(get("/api/notes").header("Authorization",bearer()).param("q"," ".repeat(201))).andExpect(status().isBadRequest());
        write("customers",uuid(),uuid(),0,Map.of("name","bad\u0000name"),400);
        var mutation=new HashMap<>(mutation("CUSTOMER",uuid(),0,Map.of("name","Name"))); mutation.remove("expectedVersion");
        push(List.of(mutation),400);
    }
    @Test void searchKeepsWildcardAndEscapeCharactersLiteral() throws Exception {
        String[] ids=hierarchy(); var data=note(ids[1]); data.put("title","100%_done!\\path");
        write("notes",ids[2],uuid(),1,data,200);
        for(String query:List.of("%_","!", "\\path", "100%_DONE"))
            mvc.perform(get("/api/notes").header("Authorization",bearer()).param("q",query))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void postgresUsesRecentAndTrigramIndexesForSelectiveFetches() throws Exception {
        String[] ids=hierarchy();
        UUID owner=UUID.fromString(body(mvc.perform(get("/api/auth/me").header("Authorization",bearer())).andReturn()).get("id").toString());
        // Test-only synthetic workload, rolled back after this test.
        db.sql("""
            INSERT INTO field_notes(id,owner_id,site_id,title,description,occurred_at,status,version,created_at,updated_at)
            SELECT md5(:seed || g::text)::uuid,:owner,:site,
                CASE WHEN g=3000 THEN 'needleuniquemarker' ELSE 'Ordinary visit ' || g END,
                'Routine inspection',CURRENT_TIMESTAMP,'OPEN',1,CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP - g * INTERVAL '1 second'
            FROM generate_series(1,6000) g
            """).param("seed",uuid()).param("owner",owner).param("site",UUID.fromString(ids[1])).update();
        // Settle bulk-load pending entries as normal GIN maintenance would do.
        db.sql("SELECT gin_clean_pending_list('notes_active_title_search_idx'::regclass)").query(Long.class).single();
        db.sql("ANALYZE field_notes").update();
        String recent=String.join("\n",db.sql("EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM field_notes WHERE owner_id=:owner AND NOT deleted ORDER BY updated_at DESC,id LIMIT 50")
            .param("owner",owner).query(String.class).list());
        assertThat(recent).contains("notes_active_recent_idx").doesNotContain("Sort Key");
        String search=String.join("\n",db.sql("EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM field_notes WHERE owner_id=:owner AND NOT deleted AND title ILIKE '%needleuniquemarker%'")
            .param("owner",owner).query(String.class).list());
        assertThat(search).contains("notes_active_title_search_idx");
        mvc.perform(get("/api/notes").header("Authorization",bearer()).param("q","needleuniquemarker"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }
    String[] hierarchy() throws Exception {
        String customer=uuid(),site=uuid(),note=uuid();
        write("customers",customer,uuid(),0,Map.of("name","Acme"),200);
        write("sites",site,uuid(),0,Map.of("siteName","North","address","Main road","customerId",customer),200);
        write("notes",note,uuid(),0,note(site),200);
        return new String[]{customer,site,note};
    }
    Map<String,Object> note(String site) { return new HashMap<>(Map.of("siteId",site,"title","Inspection","description","A crack","occurredAt","2026-10-08T08:00:00Z","status","OPEN")); }
    Map<String,Object> mutation(String type,String id,long version,Map<String,Object> data) {
        return Map.of("mutationId",uuid(),"entityType",type,"entityId",id,"operation","UPSERT","expectedVersion",version,"data",data);
    }
    void push(List<Map<String,Object>> mutations,int expected) throws Exception {
        mvc.perform(post("/api/sync/push").header("Authorization",bearer()).contentType("application/json").content(json.writeValueAsString(Map.of("mutations",mutations)))).andExpect(status().is(expected));
    }
    Map<String,Object> write(String resource,String id,String mutation,long version,Map<String,Object> data,int expected) throws Exception {
        return body(mvc.perform(put("/api/"+resource+"/"+id).header("Authorization",bearer()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("mutationId",mutation,"expectedVersion",version,"data",data)))).andExpect(status().is(expected)).andReturn());
    }
    @SuppressWarnings("unchecked") Map<String,Object> body(MvcResult result) throws Exception { return json.readValue(result.getResponse().getContentAsString(),Map.class); }
    @SuppressWarnings("unchecked") Map<String,Object> cast(Object value) { return (Map<String,Object>)value; }
    String uuid() { return UUID.randomUUID().toString(); }
    String bearer() { return "Bearer "+token; }
}
