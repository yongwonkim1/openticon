<template>
  <div class="w-full h-28 overflow-hidden" style="user-select: none; background: radial-gradient(circle at center, #e0e0e5 0%, #c0c0c5 120%);">
    <div class="relative mx-auto max-w-screen-lg h-full">
      <p class="absolute -top-[12%] left-[70%] text-zinc-600 z-10 whitespace-nowrap rotate-[20deg]">
        <span class="material-icons text-[150px]">account_circle</span>
      </p>

      <p class="absolute top-[7%] left-[40%] text-zinc-600 z-0 whitespace-nowrap rotate-[20deg] blur-[11px] opacity-70">
        <span class="material-icons text-[90px]">person</span>
      </p>
      <p class="absolute -top-[1%] left-[53%] text-zinc-600 z-0 whitespace-nowrap rotate-[20deg] blur-[8px] opacity-70">
        <span class="material-icons text-[90px]">face</span>
      </p>
      <p class="absolute top-[32%] left-[64%] text-zinc-600 z-0 whitespace-nowrap rotate-[20deg] blur-[6.3px] opacity-60">
        <span class="material-icons text-[110px]">home</span>
      </p>
      <p class="absolute top-[16%] left-[78%] text-zinc-600 z-0 whitespace-nowrap rotate-[20deg] blur-[8.3px] opacity-70">
        <span class="material-icons text-[100px]">construction</span>
      </p>
      <p class="absolute top-[10%] left-[85%] text-zinc-600 z-0 whitespace-nowrap rotate-[20deg] blur-[10px] opacity-70">
        <span class="material-icons text-[80px]">brush</span>
      </p>
      <p class="absolute -bottom-2 left-[5%] text-6xl md:text-7xl font-nnsqneo-heavy text-zinc-600 z-10 whitespace-nowrap transition-all duration-300">마이페이지</p>
    </div>
  </div>
</template>

<style scoped>
.bg-text-blur {
    filter: blur(1px);
    @media (max-width: 768px) {
        filter: blur(calc((768px - 100vw) * 0.01 + 1px));
    }
}
</style>