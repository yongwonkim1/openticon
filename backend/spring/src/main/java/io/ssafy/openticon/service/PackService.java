package io.ssafy.openticon.service;

import io.ssafy.openticon.controller.response.EmoticonPackResponseDto;
import io.ssafy.openticon.controller.response.PackDownloadResponseDto;
import io.ssafy.openticon.controller.response.PackInfoResponseDto;
import io.ssafy.openticon.dto.EmoticonPack;
import io.ssafy.openticon.dto.ImageUrl;
import io.ssafy.openticon.entity.*;
import io.ssafy.openticon.exception.ErrorCode;
import io.ssafy.openticon.exception.OpenticonException;
import io.ssafy.openticon.repository.PackRepository;
import io.ssafy.openticon.repository.TagListRepository;
import io.ssafy.openticon.repository.TagRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;


import javax.imageio.ImageIO;
import javax.security.sasl.AuthenticationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PackService {

    @Value("${spring.image-server-url}")
    private String imageServerUrl;

    private final WebClient webClient;
    private final PackRepository packRepository;
    private final MemberService memberService;
    private final EmoticonService emoticonService;
    private final PermissionService permissionService;
    private final TagRepository tagRepository;
    private final TagListRepository tagListRepository;
    private final PurchaseHistoryService purchaseHistoryService;
    private final SafeSearchService safeSearchService;

    public PackService(WebClient webClient, PackRepository packRepository, MemberService memberService, EmoticonService emoticonService, PermissionService permissionService, TagRepository tagRepository, TagListRepository tagListRepository, PurchaseHistoryService purchaseHistoryService, SafeSearchService safeSearchService){
        this.webClient=webClient;
        this.packRepository=packRepository;
        this.memberService = memberService;
        this.emoticonService=emoticonService;
        this.permissionService = permissionService;
        this.tagRepository = tagRepository;
        this.tagListRepository = tagListRepository;
        this.purchaseHistoryService = purchaseHistoryService;
        this.safeSearchService = safeSearchService;
    }

    @Transactional
    public String emoticonPackUpload(EmoticonPack emoticonPack, boolean applyWhiteBackground){
        try{
            List<MultipartFile> infoImages = new ArrayList<>();
            infoImages.add(emoticonPack.getThumbnailImg());
            infoImages.add(emoticonPack.getListImg());
            boolean problematicInfoImage = safeSearchService.detectSafeSearch(infoImages); // true면 이상한 이미지


            List<MultipartFile> emoticonList = emoticonPack.getEmoticons();
            MultipartFile thumbnailImg= emoticonPack.getThumbnailImg();
            MultipartFile listImg= emoticonPack.getListImg();
            String thumbnailImgUrl=saveImage(thumbnailImg,applyWhiteBackground);
            String listImgUrl=saveImage(listImg,applyWhiteBackground);
            List<String> emoticonsUrls=new ArrayList<>();



            MemberEntity member=memberService.getMemberByEmail(emoticonPack.getUsername()).orElseThrow();
            EmoticonPackEntity emoticonPackEntity=new EmoticonPackEntity(emoticonPack,member, thumbnailImgUrl,listImgUrl);


            boolean problematicImage = false;
            for (int i = 0; i < emoticonList.size(); i += 16) {
                int end = Math.min(i + 16, emoticonList.size());
                List<MultipartFile> subList = emoticonList.subList(i, end);

                if (safeSearchService.detectSafeSearch(subList)) {
                    problematicImage = true;
                }
            }

            if(problematicImage || problematicInfoImage) emoticonPackEntity.setBlacklist(true);
            packRepository.save(emoticonPackEntity);
            // 여기 부분
            for(MultipartFile emoticon: emoticonList){
                emoticonsUrls.add(saveImage(emoticon,applyWhiteBackground));
            }
            emoticonService.saveEmoticons(emoticonsUrls,emoticonPackEntity);

            // 태그 정보 추가
            List<String> tagNames = emoticonPack.getTags();
            for(String tag : tagNames){
                TagEntity findTagEntity = null;
                Optional<TagEntity> getTagEntity = tagRepository.findByTagName(tag);
                if(getTagEntity.isEmpty()){
                    TagEntity tagEntity = TagEntity.builder()
                            .tagName(tag)
                            .build();
                    tagRepository.save(tagEntity);
                    findTagEntity = tagEntity;
                }else{
                    findTagEntity = getTagEntity.get();
                }
                TagListEntity tagListEntity = TagListEntity.builder()
                        .emoticonPack(emoticonPackEntity)
                        .tag(findTagEntity)
                        .build();
                tagListRepository.save(tagListEntity);
            }
            return emoticonPackEntity.getShareLink();
        }catch (IOException e){
            throw new RuntimeException(e.getMessage());
        }
    }


    private String saveImage(MultipartFile image, boolean applyWhiteBackground){
        String uploadServerUrl = imageServerUrl+ "/upload/image";

        File tempFile = null;
        try {
            BufferedImage originalImage = ImageIO.read(image.getInputStream());
            BufferedImage processedImage = originalImage;
            if (applyWhiteBackground && "image/png".equals(image.getContentType())) {
                processedImage = convertTransparentToWhiteBackground(originalImage);
            }

            tempFile = File.createTempFile("upload", ".png");
            ImageIO.write(processedImage, "png", tempFile);

            ImageUrl imageUrl = webClient.post()
                    .uri(uploadServerUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(createMultipartBody(tempFile, image.getOriginalFilename()))
                    .retrieve()
                    .bodyToMono(ImageUrl.class)
                    .block();

            return imageUrl.getUrl();

        } catch (IOException e) {
            throw new RuntimeException("Failed to save image",e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete(); // 임시 파일 삭제
            }
        }


    }

    private MultiValueMap<String, Object> createMultipartBody(File file, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("upload", new FileSystemResource(file));
        return body;
    }

    public PackInfoResponseDto getPackInfo(String uuid, String email) throws AuthenticationException {

        EmoticonPackEntity emoticonPackEntity=packRepository.findByShareLink(uuid);

        if(!validatePrivatePack(email,emoticonPackEntity.getId()) && !emoticonPackEntity.isPublic()){
            throw new OpenticonException(ErrorCode.PRIVATE_PACK);
        }

        List<String> emoticons=emoticonService.getEmoticons(emoticonPackEntity.getId());

        return new PackInfoResponseDto(emoticonPackEntity,emoticons);
    }

    private boolean validatePrivatePack(String email, Long packId){

        if(memberService.getMemberByEmail(email).isEmpty()){
            throw new IllegalArgumentException();
        }
        Long memberId=memberService.getMemberByEmail(email).get().getId();
        List<Long> accessedUsers=permissionService.permissionUsers(packId);

        for(Long user: accessedUsers){
            if(memberId.equals(user)){
                return true;
            }
        }
        return false;
    }

    public PackInfoResponseDto getPackInfoByPackId(String emoticonPackId){

        Long packId=Long.parseLong(emoticonPackId);
        if(packRepository.findById(packId).isEmpty()){
            throw new IllegalArgumentException("해당하는 EmoticonPack 이 없음");
        }

        EmoticonPackEntity emoticonPackEntity=packRepository.findById(packId).get();

        if(!emoticonPackEntity.isPublic()){
            throw new OpenticonException(ErrorCode.PRIVATE_PACK);
        }

        List<String> emoticons=emoticonService.getEmoticons(emoticonPackEntity.getId());

        return new PackInfoResponseDto(emoticonPackEntity,emoticons);
    }

    public Page<EmoticonPackResponseDto> search(String query, String type, Pageable pageable) {
        if (query == null || query.isEmpty()) {
            return packRepository.findAllByIsPublicTrueAndIsBlacklistFalse(pageable).map(EmoticonPackResponseDto::new); // 검색어가 없으면 전체 조회
        }

        // TODO: type 기준
        switch (type.toLowerCase()) {
            case "title":
                return packRepository.findByTitleContaining(query, pageable).map(EmoticonPackResponseDto::new); // 부분 일치
            case "tag":
                return packRepository.findByTag(query, pageable).map(EmoticonPackResponseDto::new);   // 부분 일치
            case "author":
                return packRepository.findByAuthorContaining(query, pageable).map(EmoticonPackResponseDto::new); // 부분 일치
            default:
                return packRepository.findAll(pageable).map(EmoticonPackResponseDto::new); // 기본 전체 조회
        }
    }

    public PackDownloadResponseDto downloadPack(String email, Long packId) {
        MemberEntity member=memberService.getMemberByEmail(email).orElseThrow();
        EmoticonPackEntity emoticonPackEntity=packRepository.findById(packId).orElseThrow();
        if(!purchaseHistoryService.isMemberPurchasePack(member,emoticonPackEntity)){
            throw new OpenticonException(ErrorCode.ACCESS_DENIED);
        }

        String thumbnailImg=emoticonPackEntity.getThumbnailImg();
        String listImg=emoticonPackEntity.getListImg();
        List<String> emoticons=emoticonService.getEmoticons(packId);
        return new PackDownloadResponseDto(thumbnailImg,listImg,emoticons);
    }

    public EmoticonPackEntity getPackById(Long packId){
        return packRepository.findById(packId).orElseThrow();
    }

    // 배경을 흰색으로 설정하는 메서드
    private BufferedImage convertTransparentToWhiteBackground(BufferedImage originalImage) {
        BufferedImage newImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = newImage.createGraphics();
        g2d.setPaint(Color.BLUE); // 흰색 배경 설정
        g2d.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();

        return newImage;
    }
}
