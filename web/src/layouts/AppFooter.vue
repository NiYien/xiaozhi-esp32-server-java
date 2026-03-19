<script setup lang="ts">
import { CopyrightOutlined } from '@ant-design/icons-vue'

const currentYear = new Date().getFullYear()
</script>

<template>
  <div class="app-footer">
    <div class="footer-copyright">
      Copyright
      <CopyrightOutlined style="margin: 0 4px" />
      {{ currentYear }} All rights reserved
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-footer {
  text-align: center;
  padding: 2px 0;
}

.footer-copyright {
  color: var(--ant-color-text-secondary);
  font-size: 14px;
  transition: all 0.3s;
}
</style>
