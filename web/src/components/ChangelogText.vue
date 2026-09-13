<script setup>
/**
 * Renders the inline-markdown node arrays produced by useChangelog's parser
 * as real elements — no v-html, so CDN-provided text can never inject markup.
 */
defineProps({
    nodes: { type: Array, required: true },
})
</script>

<template>
    <template v-for="(n, i) in nodes" :key="i">
        <span v-if="n.t === 'text'">{{ n.v }}</span>
        <strong v-else-if="n.t === 'bold'"><ChangelogText :nodes="n.children" /></strong>
        <code v-else-if="n.t === 'code'" class="clg-code">{{ n.v }}</code>
        <a v-else :href="n.href" target="_blank" rel="noopener noreferrer">{{ n.v }}</a>
    </template>
</template>
