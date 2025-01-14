package io.beatmaps.maps.testplay

import external.Dropzone
import external.ReCAPTCHA
import external.TimeAgo
import io.beatmaps.common.api.EMapState
import io.beatmaps.api.MapDetail
import io.beatmaps.index.ModalComponent
import io.beatmaps.upload.simple
import kotlinx.datetime.Instant
import org.w3c.dom.HTMLElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.*
import react.router.dom.RouteResultHistory
import react.setState

enum class EventType {
    Feedback, Play, Version
}
data class Event(val type: EventType, val state: EMapState?, val title: String, val body: String, val time: Instant, val hash: String, val userId: Int? = null)

external interface TimelineProps : RProps {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var loggedInId: Int?
    var reloadMap: () -> Unit
    var history: RouteResultHistory
    var modal: RReadableRef<ModalComponent>
}

external interface TimelineState : RState {
    var errors: List<String>
    var loading: Boolean
}

@JsExport
class Timeline : RComponent<TimelineProps, TimelineState>() {
    private val captchaRef = createRef<ReCAPTCHA>()
    private val progressBarInnerRef = createRef<HTMLElement>()

    override fun componentWillMount() {
        setState {
            errors = listOf()
            loading = false
        }
    }

    override fun RBuilder.render() {
        val versionTimes = props.mapInfo.versions.map { it.hash to it.createdAt }.toMap()
        val events = props.mapInfo.versions.flatMap { v ->
            (v.testplays?.let {
                it.flatMap { t ->
                    listOf(
                        Event(EventType.Play, null, t.user.name, " played the map", t.createdAt, v.hash),
                        t.feedbackAt?.let { at -> Event(EventType.Feedback, null, t.user.name, t.feedback ?: "", at, v.hash, t.user.id) }
                    )
                }
            } ?: listOf()) + listOf(
                Event(EventType.Version, v.state, "New version uploaded", v.feedback ?: "", v.createdAt, v.hash)
            )
            //.sortedWith(compareBy<MapDifficulty> { d -> d.characteristic }.thenByDescending { d -> d.difficulty })?.first()
        }.filterNotNull().sortedWith(compareByDescending<Event> { versionTimes[it.hash] }.thenByDescending { it.time })

        div("timeline") {
            // Watch out, this must be at the top
            div("line text-muted") {}

            val latestVersion = props.mapInfo.latestVersion()
            val givenFeedback = latestVersion?.testplays?.any { it.user.id == props.loggedInId && it.feedbackAt != null } == true || props.loggedInId == null
            if (props.isOwner) {
                article("card") {
                    div("card-header icon bg-success") {
                        i("fas fa-plus") {}
                    }
                    div("card-body") {
                        Dropzone.default {
                            simple(props.history, state.loading, state.errors.isNotEmpty(), progressBarInnerRef,
                                "Drag and drop some files here, or click to upload a new version", captchaRef, {
                                setState {
                                    loading = true
                                }
                                it.append("mapId", props.mapInfo.id.toString())
                            }, {
                                setState {
                                    errors = it
                                    loading = false
                                }
                            }) {
                                setState {
                                    errors = listOf()
                                    loading = false
                                }
                                props.reloadMap()
                            }
                        }
                        if (!state.loading) {
                            state.errors.forEach {
                                div("invalid-feedback") {
                                    +it
                                }
                            }
                        }
                    }
                }
            } else if (!givenFeedback && latestVersion != null) {
                newFeedback {
                    hash = latestVersion.hash
                    captcha = captchaRef
                }
            }

            var first = true
            events.forEach {
                when (it.type) {
                    EventType.Version -> {
                        val version = props.mapInfo.versions.find { v -> v.hash == it.hash }
                        version {
                            hash = it.hash
                            diffs = version?.diffs
                            isOwner = props.isOwner
                            feedback = it.body
                            firstVersion = first
                            time = it.time.toString()
                            state = it.state
                            reloadMap = props.reloadMap
                            mapId = props.mapInfo.id
                            modal = props.modal
                        }
                        first = false
                    }
                    EventType.Feedback ->
                        feedback {
                            hash = it.hash
                            isOwner = it.userId != null && it.userId == props.loggedInId
                            feedback = it.body
                            name = it.title
                            time = it.time.toString()
                        }
                    EventType.Play -> {
                        article ("card card-outline") {
                            div("card-header icon bg-warning") {
                                i("fas fa-vial") {}
                            }
                            div("card-body") {
                                strong {
                                    +it.title
                                }
                                +it.body
                            }
                            small {
                                TimeAgo.default {
                                    attrs.date = it.time.toString()
                                }
                                br {}
                                +it.hash
                            }
                        }
                    }
                }
            }

            ReCAPTCHA.default {
                attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
                attrs.size = "invisible"
                ref = captchaRef
            }
        }
    }
}

fun RBuilder.timeline(handler: TimelineProps.() -> Unit): ReactElement {
    return child(Timeline::class) {
        this.attrs(handler)
    }
}