package com.task.fieldnotes.photo;

import com.task.fieldnotes.common.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PhotoService {
    public record Photo(UUID id,String contentType,int size) {}
    public static final int MAX=5*1024*1024;
    private final PhotoRepository photos;
    public PhotoService(PhotoRepository photos) { this.photos=photos; }
    @Transactional
    public Photo upload(UUID owner,UUID id,String contentType,byte[] bytes) {
        if(!Set.of("image/jpeg","image/png").contains(contentType==null?"":contentType)) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Only image/jpeg and image/png are supported");
        if(bytes.length>MAX) throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,"Photo exceeds 5 MiB");
        boolean jpeg=bytes.length>=3 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8 && bytes[2]==(byte)0xff;
        boolean png=bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
        if(!(contentType.equals("image/jpeg")?jpeg:png)) throw ApiException.bad("Photo header does not match content type");
        String digest=Digests.sha256(bytes);
        photos.lockOwner(owner);
        var existing=photos.find(owner,id);
        if(existing.isPresent()) {
            if(!existing.get().digest().equals(digest) || !existing.get().contentType().equals(contentType)) throw ApiException.conflict("Photo ID already has different content");
        } else photos.insert(owner,id,contentType,bytes,digest);
        return new Photo(id,contentType,bytes.length);
    }
    public PhotoRepository.Stored download(UUID owner,UUID id) { return photos.find(owner,id).orElseThrow(ApiException::missing); }
}
