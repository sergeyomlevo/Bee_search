// DeepSeek Harness → personal-core audit v2 adapter (observation only).
//
// Transports (verified live in this DSH installation; see README § DSH integration):
//   - `session/event` — the durable, post-commit session-log firehose, an
//     ordinary `emit` event. Carries `tool/call` (attempt) and the turn
//     boundaries `turn/start` / `turn/end`.
//   - `tools/result` — the live observe-only pipeline notification fired after a
//     tool settled. Carries the same call identity plus the settled outcome.
//
// Each lifecycle phase is taken from exactly one transport, so nothing is
// double-counted: the attempt and the turn boundaries come from the durable log
// feed, the settled outcome from the live pipeline notification.
//
// The adapter only maps payload shape onto the existing v2 observer. It contains
// no activity classification, no governance evaluation and no reporting logic,
// and it never decides, blocks, allows or approves anything. Every listener body
// is fail-open: an adapter fault must never break a turn.
//
// Privacy: only scalar identity/timing leaves and file-path arguments are read.
// Prompts, model text, command text, tool results and file contents are never
// read, let alone stored. Arguments cross the boundary stripped down to the
// resource keys the observer already understands.

import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const here = path.dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = path.resolve(here, '..', '..')
const handler = require('./hook-handler.js')

export const name = 'bee-search-personal-core-audit-dsh-adapter'

// Confidence labels recorded on every produced record, so a reader can separate
// what was observed live from what was read out of a type declaration.
export const CONFIDENCE = {
  attemptIdentity: 'verified_live',
  settledOutcome: 'verified_live',
  sessionAndCwd: 'verified_live',
  turnAndStep: 'verified_live',
  sequenceAndTime: 'verified_live',
  turnBoundaryReason: 'verified_type_declaration',
  resourceArguments: 'verified_type_declaration',
}

// Only the resource keys the observer's activity layer consumes; everything else
// in a tool's arguments stays inside the harness.
const RESOURCE_KEYS = ['file_path', 'filePath', 'path', 'file', 'target', 'resource', 'destination', 'source', 'files']
const MAX_ARGUMENT_TEXT = 8192

function scalar(value) {
  return typeof value === 'string' && value ? value : null
}

function number(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function plainObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : undefined
}

function normalizePath(value) {
  return scalar(value) ? path.resolve(value).toLowerCase() : null
}

/** Reduce a tool's arguments to resource-bearing values, or return undefined. */
export function pickResourceArguments(argumentsValue) {
  let source = argumentsValue
  if (typeof source === 'string') {
    if (source.length > MAX_ARGUMENT_TEXT) return undefined
    try {
      source = JSON.parse(source)
    } catch (error) {
      return undefined
    }
  }
  const object = plainObject(source)
  if (!object) return undefined
  const picked = {}
  for (const key of RESOURCE_KEYS) {
    const value = object[key]
    if (typeof value === 'string') picked[key] = value.slice(0, 512)
    else if (Array.isArray(value)) {
      const strings = value.filter((item) => typeof item === 'string').slice(0, 20).map((item) => item.slice(0, 512))
      if (strings.length) picked[key] = strings
    }
  }
  return Object.keys(picked).length ? picked : undefined
}

function sessionIdOf(session) {
  return scalar(session && session.id)
}

function cwdOf(session) {
  return normalizePath(session && session.header && session.header.cwd)
}

/** True when this session belongs to one of the configured workspaces. */
export function sessionInWorkspaces(session, workspaceRoots) {
  const cwd = cwdOf(session)
  if (!cwd) return false
  return workspaceRoots.some((root) => normalizePath(root) === cwd)
}

/**
 * Map one DSH `session/event` payload onto the v2 observer's event contract, or
 * return null when the event is not one this adapter observes.
 *
 * `hook_event_name` is the observer's harness-neutral lifecycle slot; the native
 * DSH event type, turn, step, sequence and boundary reason travel in `source`,
 * so the normalization stays auditable. Fields DSH has no analog for (`turn_id`,
 * `agent_id`) stay null instead of being synthesized.
 */
export function mapDshSessionEvent(session, event) {
  const payload = plainObject(event)
  if (!payload) return null
  const type = scalar(payload.type)
  if (type !== 'tool/call' && type !== 'turn/start' && type !== 'turn/end') return null

  const data = plainObject(payload.data) || {}
  const source = {
    harness: 'deepseek-harness',
    transport: 'session/event',
    event_type: type,
    seq: number(payload.seq),
    event_time_ms: number(payload.time),
    turn: number(data.turn),
    step: number(data.step),
  }

  if (type === 'tool/call') {
    const callId = scalar(data.callId)
    const toolName = scalar(data.name)
    if (!callId || !toolName) return null
    const toolInput = pickResourceArguments(data.arguments)
    return {
      hook_event_name: 'PreToolUse',
      session_id: sessionIdOf(session),
      turn_id: null,
      agent_id: null,
      tool_use_id: callId,
      tool_name: toolName,
      ...(toolInput ? { tool_input: toolInput } : {}),
      source: { ...source, mapping_confidence: CONFIDENCE.attemptIdentity },
    }
  }

  const reason = plainObject(data.reason)
  const kind = scalar(reason && reason.kind)
  const boundary = type === 'turn/start' ? 'UserPromptSubmit' : (kind === 'aborted' ? 'Interrupt' : 'Stop')
  return {
    hook_event_name: boundary,
    session_id: sessionIdOf(session),
    turn_id: null,
    agent_id: null,
    tool_use_id: null,
    tool_name: null,
    source: { ...source, mapping_confidence: CONFIDENCE.turnBoundaryReason, boundary_reason: kind },
  }
}

/**
 * Map one live `tools/result` notification onto the v2 observer's event
 * contract. `exec.callId` is the same identity the attempt was recorded under,
 * which is what pairs the two lifecycle records.
 */
export function mapDshToolResult(exec, result) {
  const execution = plainObject(exec)
  if (!execution) return null
  const callId = scalar(execution.callId)
  const toolName = scalar(execution.name)
  if (!callId || !toolName) return null
  const agent = plainObject(execution.agent)
  const outcome = plainObject(result)
  const toolInput = pickResourceArguments(execution.arguments)
  return {
    hook_event_name: 'PostToolUse',
    session_id: agent ? sessionIdOf(agent.session) : null,
    turn_id: null,
    agent_id: null,
    tool_use_id: callId,
    tool_name: toolName,
    tool_response: { is_error: Boolean(outcome && outcome.isError === true) },
    ...(toolInput ? { tool_input: toolInput } : {}),
    source: {
      harness: 'deepseek-harness',
      transport: 'tools/result',
      event_type: 'tools/result',
      mapping_confidence: CONFIDENCE.settledOutcome,
    },
  }
}

function record(mapped, eventTimeMs, options) {
  handler.recordEvent(mapped, {
    repoRoot: options.repoRoot,
    ...(options.auditDir ? { auditDir: options.auditDir } : {}),
    ...(typeof eventTimeMs === 'number' && Number.isFinite(eventTimeMs)
      ? { now: new Date(eventTimeMs).toISOString() }
      : {}),
  })
}

export function apply(ctx, config) {
  const configured = Array.isArray(config && config.workspaceRoots) ? config.workspaceRoots : []
  const workspaceRoots = (configured.length ? configured : [REPO_ROOT]).filter((value) => typeof value === 'string' && value)
  const options = {
    repoRoot: scalar(config && config.repoRoot) || REPO_ROOT,
    auditDir: scalar(config && config.auditDir) || undefined,
  }

  function guard(action) {
    try {
      action()
    } catch (error) {
      // Observation only: never let an audit fault affect the observed turn.
      try {
        console.error(`personal-core audit DSH adapter fault: ${(error && error.message) || error}`)
      } catch (ignored) { /* nothing left to do */ }
    }
  }

  ctx.on('session/event', (session, event) => {
    guard(() => {
      if (!sessionInWorkspaces(session, workspaceRoots)) return
      const mapped = mapDshSessionEvent(session, event)
      if (mapped === null) return
      record(mapped, plainObject(event) && typeof event.time === 'number' ? event.time : undefined, options)
    })
  })

  ctx.on('tools/result', (exec, result) => {
    guard(() => {
      const agent = plainObject(exec) ? plainObject(exec.agent) : undefined
      if (!agent || !sessionInWorkspaces(agent.session, workspaceRoots)) return
      const mapped = mapDshToolResult(exec, result)
      if (mapped === null) return
      record(mapped, undefined, options)
    })
  })
}
