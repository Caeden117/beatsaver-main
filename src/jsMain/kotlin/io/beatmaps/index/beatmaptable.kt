package io.beatmaps.index
import Axios
import CancelTokenSource
import generateConfig
import io.beatmaps.api.MapDetail
import io.beatmaps.api.SearchOrder
import io.beatmaps.api.SearchResponse
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.dom.div
import react.dom.p
import react.dom.table
import react.dom.tbody
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
import react.setState

external interface BeatmapTableProps : RProps {
    var search: SearchParams
    var user: Int?
    var wip: Boolean
    var modal: RReadableRef<ModalComponent>
    var history: RouteResultHistory
}

data class SearchParams(val search: String, val automapper: Boolean?, val minNps: Float?, val maxNps: Float?, val chroma: Boolean?, val sortOrder: SearchOrder,
                        val from: String?, val to: String?, val noodle: Boolean?, val ranked: Boolean?, val fullSpread: Boolean?, val me: Boolean?, val cinema: Boolean?)

external interface BeatmapTableState : RState {
    var page: Int
    var loading: Boolean
    var shouldClear: Boolean
    var songs: List<MapDetail>
    var user: UserDetail?
    var token: CancelTokenSource
}

@JsExport
class BeatmapTable : RComponent<BeatmapTableProps, BeatmapTableState>() {

    override fun componentWillMount() {
        setState {
            page = 0
            loading = false
            shouldClear = false
            songs = listOf()
            user = null
            token = Axios.CancelToken.source()
        }
    }

    override fun componentDidMount() {
        window.onscroll = ::handleScroll

        loadNextPage()
    }

    override fun componentWillUpdate(nextProps: BeatmapTableProps, nextState: BeatmapTableState) {
        if (props.user != nextProps.user || props.wip != nextProps.wip || props.search !== nextProps.search) {
            state.token.cancel("Another request started")
            nextState.apply {
                page = 0
                loading = false
                shouldClear = true
                token = Axios.CancelToken.source()
            }

            window.setTimeout(::loadNextPage, 0)
        }
    }

    private fun getUrl() = if (props.wip) {
            "/api/maps/wip/${state.page}"
        } else if (props.user != null) {
            "${Config.apibase}/maps/uploader/${props.user}/${state.page}"
        } else {
            "${Config.apibase}/search/text/${state.page}?sortOrder=${props.search.sortOrder}" +
                    (if (props.search.automapper != null) "&automapper=${props.search.automapper}" else "") +
                    (if (props.search.chroma != null) "&chroma=${props.search.chroma}" else "") +
                    (if (props.search.noodle != null) "&noodle=${props.search.noodle}" else "") +
                    (if (props.search.me != null) "&me=${props.search.me}" else "") +
                    (if (props.search.cinema != null) "&cinema=${props.search.cinema}" else "") +
                    (if (props.search.ranked != null) "&ranked=${props.search.ranked}" else "") +
                    (if (props.search.fullSpread != null) "&fullSpread=${props.search.fullSpread}" else "") +
                    (if (props.search.search.isNotBlank()) "&q=${props.search.search}" else "") +
                    (if (props.search.maxNps != null) "&maxNps=${props.search.maxNps}" else "") +
                    (if (props.search.minNps != null) "&minNps=${props.search.minNps}" else "") +
                    (if (props.search.from != null) "&from=${props.search.from}" else "") +
                    (if (props.search.to != null) "&to=${props.search.to}" else "")
        }

    private fun loadNextPage() {
        if (state.loading)
            return

        setState {
            loading = true
        }

        Axios.get<SearchResponse>(
            getUrl(),
            generateConfig<String, SearchResponse>(state.token.token)
        ).then {
            if (it.data.redirect != null) {
                val dyn: dynamic = props.history
                dyn.replace("/maps/" + it.data.redirect)
                return@then
            }

            state.user = it.data.user
            val mapLocal = it.data.docs

            setState {
                page = state.page + 1
                loading = mapLocal?.isEmpty() != false
                songs = mapLocal?.let { ml -> if (shouldClear) ml.toList() else songs.plus(mapLocal) } ?: songs
                shouldClear = false
            }
            if (mapLocal?.isNotEmpty() == true) {
                window.setTimeout(::handleScroll, 1)
            }
        }.catch {
            // Cancelled request?
            setState {
                loading = false
            }
        }
    }

    override fun componentWillUnmount() {
        window.onscroll = null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleScroll(e: Event) {
        val scrollPosition = window.pageYOffset
        val windowSize = window.innerHeight
        val bodyHeight = document.body?.offsetHeight ?: 0
        val headerSize = 55
        val trigger = 500

        if (bodyHeight - (scrollPosition + windowSize) + headerSize < trigger) {
            loadNextPage()
        }
    }

    override fun RBuilder.render() {
        state.user?.let {
            p("text-center") {
                +"Were you looking for "
                routeLink("/profile/${it.id}") {
                    +it.name
                }
                +"?"
            }
        }
        div("search-results") {
            state.songs.forEach {
                beatmapInfo {
                    key = it.id.toString()
                    map = it
                    version = if (props.wip) it.latestVersion() else it.publishedVersion()
                    modal = props.modal
                }
            }
        }
    }
}

fun RBuilder.beatmapTable(handler: BeatmapTableProps.() -> Unit): ReactElement {
    return child(BeatmapTable::class) {
        this.attrs(handler)
    }
}