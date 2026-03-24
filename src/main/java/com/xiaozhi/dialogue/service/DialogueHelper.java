package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.EmojiUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句子处理帮助类。
 * 有状态的实例，不要复用，用完就丢弃。
 */
class DialogueHelper implements ChatConverter {
    // 句子结束标点符号模式（中英文句号、感叹号、问号）
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("[。！？!?]");

    // 逗号、分号等停顿标点
    private static final Pattern PAUSE_PATTERN = Pattern.compile("[，、；,;]");

    // 换行符
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\n\r]");

    // 数字模式（用于检测小数点是否在数字中）
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.\\d+");

    // 最小句子长度（字符数）
    private static final int MIN_SENTENCE_LENGTH = 5;

    // 首句加速模式的最小字符数（避免碎片化）
    private static final int FIRST_SENTENCE_MIN_LENGTH = 2;

    final StringBuilder currentSentence = new StringBuilder(); // 当前句子的缓冲区
    final StringBuilder contextBuffer = new StringBuilder(); // 上下文缓冲区，用于检测数字中的小数点

    // 首句是否已发出（首句加速模式标记）
    private boolean firstSentenceEmitted = false;

    // TTFS profiling: 用于记录首句分句时刻
    private final ChatSession chatSession;

    public DialogueHelper() {
        this.chatSession = null;
    }

    public DialogueHelper(ChatSession chatSession) {
        this.chatSession = chatSession;
    }


    public void onToken(String token, FluxSink sink) {
        if (token == null || token.isEmpty()) {
            return;
        }


        // 逐字符处理token
        for (int i = 0; i < token.length();) {
            int codePoint = token.codePointAt(i);
            String charStr = new String(Character.toChars(codePoint));

            // 将字符添加到上下文缓冲区（保留最近的字符以检测数字模式）
            contextBuffer.append(charStr);
            if (contextBuffer.length() > 20) { // 保留足够的上下文
                contextBuffer.delete(0, contextBuffer.length() - 20);
            }

            // 将字符添加到当前句子缓冲区
            currentSentence.append(charStr);

            // 检查各种断句标记
            boolean shouldSendSentence = false;
            boolean isEndMark = SENTENCE_END_PATTERN.matcher(charStr).find();
            boolean isPauseMark = PAUSE_PATTERN.matcher(charStr).find();
            boolean isNewline = NEWLINE_PATTERN.matcher(charStr).find();
            boolean isEmoji = EmojiUtils.isEmoji(codePoint);

            // 检查当前句子是否包含颜文字
            boolean containsKaomoji = false;
            if (currentSentence.length() >= 3) { // 颜文字至少需要3个字符
                containsKaomoji = EmojiUtils.containsKaomoji(currentSentence.toString());
            }

            // 如果当前字符是句号，检查它是否是数字中的小数点
            if (isEndMark && charStr.equals(".")) {
                String context = contextBuffer.toString();
                Matcher numberMatcher = NUMBER_PATTERN.matcher(context);
                // 如果找到数字模式（如"0.271"），则不视为句子结束标点
                if (numberMatcher.find() && numberMatcher.end() >= context.length() - 3) {
                    isEndMark = false;
                }
            }

            // 首句加速模式：首句未发出前，停顿标点也视为强断句信号（最小长度降低为FIRST_SENTENCE_MIN_LENGTH）
            int effectiveMinLength = firstSentenceEmitted ? MIN_SENTENCE_LENGTH : FIRST_SENTENCE_MIN_LENGTH;

            // 判断是否应该发送当前句子
            if (isEndMark) {
                // 句子结束标点是强断句信号
                shouldSendSentence = true;
            } else if (isNewline) {
                // 换行符也是强断句信号
                shouldSendSentence = true;
            } else if (!firstSentenceEmitted && isPauseMark
                    && currentSentence.length() >= FIRST_SENTENCE_MIN_LENGTH) {
                // 首句加速模式：停顿标点（逗号/分号）在首句中也作为强断句信号
                shouldSendSentence = true;
            } else if ((isPauseMark || isEmoji || containsKaomoji)
                    && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                // 停顿标点、特殊标点、表情符号或颜文字在句子足够长时可以断句
                shouldSendSentence = true;
            }

            // 如果应该发送句子，且当前句子长度满足要求
            if (shouldSendSentence && currentSentence.length() >= effectiveMinLength) {
                String sentence = currentSentence.toString().trim();

                // 过滤颜文字和 Markdown 符号
                sentence = EmojiUtils.filterKaomoji(sentence);
                sentence = EmojiUtils.stripMarkdown(sentence);

                if (containsSubstantialContent(sentence, effectiveMinLength)) {

                    // TTFS profiling: 记录首句分句完成时刻
                    if (!firstSentenceEmitted && chatSession != null) {
                        chatSession.setFirstSentenceAt(Instant.now());
                    }

                    // 只有在onComplete中才会有最后一个句子
                    sink.next(sentence);

                    // 标记首句已发出，后续恢复正常断句逻辑
                    firstSentenceEmitted = true;

                    // 清空当前句子缓冲区
                    currentSentence.setLength(0);
                }
            }

            // 移动到下一个码点
            i += Character.charCount(codePoint);
        }
    }

    public void onComplete(FluxSink sink) {
        String sentence = currentSentence.toString().trim();
        // 过滤颜文字和 Markdown 符号
        sentence = EmojiUtils.filterKaomoji(sentence);
        sentence = EmojiUtils.stripMarkdown(sentence);
        if (containsSubstantialContent(sentence)) {
            sink.next(sentence);
        }
        sink.complete();
    }
    /**
     * 判断文本是否包含实质性内容（不仅仅是空白字符或标点符号）
     *
     * @param text 要检查的文本
     * @return 是否包含实质性内容
     */
    private boolean containsSubstantialContent(String text) {
        return containsSubstantialContent(text, MIN_SENTENCE_LENGTH);
    }

    /**
     * 判断文本是否包含实质性内容（不仅仅是空白字符或标点符号）
     *
     * @param text 要检查的文本
     * @param minLength 最小长度要求
     * @return 是否包含实质性内容
     */
    private boolean containsSubstantialContent(String text, int minLength) {
        if (text == null || text.trim().length() < minLength) {
            return false;
        }

        // 移除标点、空白、emoji 后，检查是否还有实质文字内容
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!EmojiUtils.isEmoji(cp)) {
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        String stripped = sb.toString().replaceAll("[\\p{P}\\s]", "");
        return stripped.length() >= 2;
    }


    // 使用句子切分处理响应
    public Flux<String> convert(Flux<String> stringFlux) {

        // 转换为句子流
        Flux<String> sentenceFlux = Flux.create(sink -> {
            stringFlux.subscribe(token -> {
                        this.onToken(token, sink);
                    },
                    sink::error,
                    () -> this.onComplete(sink));
        });
        return sentenceFlux;
    }
}
