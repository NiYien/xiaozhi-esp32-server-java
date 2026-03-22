import { ref, onUnmounted } from 'vue'

export type RecordingState = 'idle' | 'recording' | 'stopped'

const TARGET_SAMPLE_RATE = 16000

export function useAudioRecorder() {
  const state = ref<RecordingState>('idle')
  const duration = ref(0)
  const wavFile = ref<File | null>(null)
  const wavBlobUrl = ref<string | null>(null)
  const isSupported = ref(!!(navigator.mediaDevices?.getUserMedia))
  const errorMsg = ref('')

  let audioContext: AudioContext | null = null
  let mediaStream: MediaStream | null = null
  let analyserNode: AnalyserNode | null = null
  let sourceNode: MediaStreamAudioSourceNode | null = null
  let scriptNode: ScriptProcessorNode | null = null
  let animationFrameId: number | null = null
  let canvasCtx: CanvasRenderingContext2D | null = null
  let canvasEl: HTMLCanvasElement | null = null
  let rawChunks: Float32Array[] = []
  let rawSampleRate = 44100
  let timerInterval: ReturnType<typeof setInterval> | null = null

  function initCanvas(canvas: HTMLCanvasElement) {
    canvasEl = canvas
    canvasCtx = canvas.getContext('2d')
    drawIdleLine()
  }

  function drawIdleLine() {
    if (!canvasCtx || !canvasEl) return
    const w = canvasEl.width
    const h = canvasEl.height
    canvasCtx.fillStyle = '#fafafa'
    canvasCtx.fillRect(0, 0, w, h)
    canvasCtx.strokeStyle = '#d9d9d9'
    canvasCtx.lineWidth = 1
    canvasCtx.beginPath()
    canvasCtx.moveTo(0, h / 2)
    canvasCtx.lineTo(w, h / 2)
    canvasCtx.stroke()
  }

  function drawWaveform(color: string) {
    if (!canvasCtx || !canvasEl || !analyserNode) return
    const w = canvasEl.width
    const h = canvasEl.height
    const bufferLength = analyserNode.fftSize
    const dataArray = new Uint8Array(bufferLength)
    analyserNode.getByteTimeDomainData(dataArray)

    canvasCtx.fillStyle = '#fafafa'
    canvasCtx.fillRect(0, 0, w, h)
    canvasCtx.lineWidth = 2
    canvasCtx.strokeStyle = color
    canvasCtx.beginPath()

    const sliceWidth = w / bufferLength
    let x = 0
    for (let i = 0; i < bufferLength; i++) {
      const v = dataArray[i] / 128.0
      const y = (v * h) / 2
      if (i === 0) canvasCtx.moveTo(x, y)
      else canvasCtx.lineTo(x, y)
      x += sliceWidth
    }
    canvasCtx.lineTo(w, h / 2)
    canvasCtx.stroke()
  }

  function animateWaveform() {
    if (state.value !== 'recording') return
    drawWaveform('#52c41a')
    animationFrameId = requestAnimationFrame(animateWaveform)
  }

  async function start() {
    errorMsg.value = ''
    if (!isSupported.value) {
      errorMsg.value = 'notSupported'
      return
    }

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      errorMsg.value = 'permissionDenied'
      return
    }

    audioContext = new AudioContext()
    rawSampleRate = audioContext.sampleRate
    sourceNode = audioContext.createMediaStreamSource(mediaStream)

    analyserNode = audioContext.createAnalyser()
    analyserNode.fftSize = 2048
    sourceNode.connect(analyserNode)

    scriptNode = audioContext.createScriptProcessor(4096, 1, 1)
    rawChunks = []
    scriptNode.onaudioprocess = (e) => {
      const data = e.inputBuffer.getChannelData(0)
      rawChunks.push(new Float32Array(data))
    }
    analyserNode.connect(scriptNode)
    scriptNode.connect(audioContext.destination)

    duration.value = 0
    timerInterval = setInterval(() => {
      duration.value += 0.1
    }, 100)

    state.value = 'recording'
    animateWaveform()
  }

  async function stop() {
    if (state.value !== 'recording') return

    if (timerInterval) {
      clearInterval(timerInterval)
      timerInterval = null
    }
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }

    // Draw final static waveform in grey before disconnecting
    if (analyserNode && canvasCtx && canvasEl) {
      drawWaveform('#bfbfbf')
    }

    scriptNode?.disconnect()
    analyserNode?.disconnect()
    sourceNode?.disconnect()
    mediaStream?.getTracks().forEach((t) => t.stop())

    state.value = 'stopped'

    // Process audio: resample to 16kHz and create WAV
    const totalLength = rawChunks.reduce((sum, c) => sum + c.length, 0)
    const rawPcm = new Float32Array(totalLength)
    let offset = 0
    for (const chunk of rawChunks) {
      rawPcm.set(chunk, offset)
      offset += chunk.length
    }

    const resampled = await resampleTo16k(rawPcm, rawSampleRate)
    const wavBlob = encodeWav(resampled, TARGET_SAMPLE_RATE)
    wavFile.value = new File([wavBlob], 'recording.wav', { type: 'audio/wav' })

    if (wavBlobUrl.value) URL.revokeObjectURL(wavBlobUrl.value)
    wavBlobUrl.value = URL.createObjectURL(wavBlob)

    if (audioContext) {
      await audioContext.close()
      audioContext = null
    }
  }

  function reset() {
    if (state.value === 'recording') {
      if (timerInterval) {
        clearInterval(timerInterval)
        timerInterval = null
      }
      if (animationFrameId) {
        cancelAnimationFrame(animationFrameId)
        animationFrameId = null
      }
      scriptNode?.disconnect()
      analyserNode?.disconnect()
      sourceNode?.disconnect()
      mediaStream?.getTracks().forEach((t) => t.stop())
      audioContext?.close()
      audioContext = null
    }

    state.value = 'idle'
    duration.value = 0
    rawChunks = []
    wavFile.value = null
    if (wavBlobUrl.value) {
      URL.revokeObjectURL(wavBlobUrl.value)
      wavBlobUrl.value = null
    }
    errorMsg.value = ''
    drawIdleLine()
  }

  async function resampleTo16k(pcm: Float32Array, srcRate: number): Promise<Float32Array> {
    if (srcRate === TARGET_SAMPLE_RATE) return pcm

    const durationSec = pcm.length / srcRate
    const offlineCtx = new OfflineAudioContext(1, Math.ceil(durationSec * TARGET_SAMPLE_RATE), TARGET_SAMPLE_RATE)
    const buffer = offlineCtx.createBuffer(1, pcm.length, srcRate)
    buffer.getChannelData(0).set(pcm)
    const source = offlineCtx.createBufferSource()
    source.buffer = buffer
    source.connect(offlineCtx.destination)
    source.start(0)
    const rendered = await offlineCtx.startRendering()
    return rendered.getChannelData(0)
  }

  function encodeWav(samples: Float32Array, sampleRate: number): Blob {
    const int16 = new Int16Array(samples.length)
    for (let i = 0; i < samples.length; i++) {
      const s = Math.max(-1, Math.min(1, samples[i]))
      int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
    }

    const byteLength = int16.length * 2
    const buffer = new ArrayBuffer(44 + byteLength)
    const view = new DataView(buffer)

    // RIFF header
    writeString(view, 0, 'RIFF')
    view.setUint32(4, 36 + byteLength, true)
    writeString(view, 8, 'WAVE')

    // fmt sub-chunk
    writeString(view, 12, 'fmt ')
    view.setUint32(16, 16, true)           // sub-chunk size
    view.setUint16(20, 1, true)            // PCM format
    view.setUint16(22, 1, true)            // mono
    view.setUint32(24, sampleRate, true)   // sample rate
    view.setUint32(28, sampleRate * 2, true) // byte rate
    view.setUint16(32, 2, true)            // block align
    view.setUint16(34, 16, true)           // bits per sample

    // data sub-chunk
    writeString(view, 36, 'data')
    view.setUint32(40, byteLength, true)

    const output = new Int16Array(buffer, 44)
    output.set(int16)

    return new Blob([buffer], { type: 'audio/wav' })
  }

  function writeString(view: DataView, offset: number, str: string) {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i))
    }
  }

  onUnmounted(() => {
    if (state.value === 'recording') {
      mediaStream?.getTracks().forEach((t) => t.stop())
      audioContext?.close()
    }
    if (timerInterval) clearInterval(timerInterval)
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
    if (wavBlobUrl.value) URL.revokeObjectURL(wavBlobUrl.value)
  })

  return {
    state,
    duration,
    wavFile,
    wavBlobUrl,
    isSupported,
    errorMsg,
    initCanvas,
    start,
    stop,
    reset,
  }
}
