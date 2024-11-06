import { defineStore } from "pinia";
import apiClient from "@/util/apiClient";
import type { EmoticonPackSearchList } from "@/types/emoticonPackSearchList";
import type { EmoticonPack } from "@/types/emoticonPack";
import type { EmoticonPackUploadInfo, EmoticonPackUploadFiles } from "@/types/emoticonPackUpload";
import { ref } from "vue";

export const useEmoticonPackStore = defineStore("emoticonPack", () => {
  const getEmoticonPackData = async (id: number): Promise<EmoticonPack> => {
    const response = await apiClient.get<EmoticonPack>(`/emoticonpacks/info`, {
      params: {
        emoticonPackId: id,
      },
    });
    return response.data;
  };

  const getNewEmoticonPackList = async (page: number, size: number): Promise<EmoticonPackSearchList> => {
    const response = await apiClient.get(`/emoticonpacks/search`, {
      params: {
        page: page,
        size: size,
        sort: "new",
      },
    });
    return response.data;
  };

  const getPopularEmoticonPackList = async (page: number, size: number): Promise<EmoticonPackSearchList> => {
    const response = await apiClient.get(`/emoticonpacks/search`, {
      params: {
        page: page,
        size: size,
        sort: "most",
      },
    });
    return response.data;
  };

  const searchEmoticonPack = async (query: string, type: string, page: number, size: number): Promise<EmoticonPackSearchList> => {
    const response = await apiClient.get(`/emoticonpacks/search`, {
      params: {
        query: query,
        type: type,
        page: page,
        size: size,
      },
    });
    return response.data;
  };

  const uploadEmoticonPack = async (
    packInfo: EmoticonPackUploadInfo,
    files: EmoticonPackUploadFiles
  ): Promise<void> => {
    const formData = new FormData();
    
    // packInfo를 JSON 문자열로 변환하여 추가
    formData.append("packInfo", JSON.stringify(packInfo));
    
    // 파일들 추가
    formData.append("thumbnail_img", files.thumbnailImg);
    formData.append("list_img", files.listImg);
    
    // 여러 이모티콘 파일들 추가
    files.emoticons.forEach((emoticon) => {
      formData.append("emoticons", emoticon);
    });

    return apiClient.post("/emoticonpacks/upload", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  };

  return { getEmoticonPackData, getNewEmoticonPackList, getPopularEmoticonPackList, searchEmoticonPack, uploadEmoticonPack };
});
