package no.breale17.post.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.breale17.dto.PostDto
import no.breale17.dto.UserDto
import no.breale17.post.service.PostService
import no.utils.pagination.PageDto
import no.utils.wrapper.WrappedResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.Principal

@Api(value = "/posts", description = "Retrieves posts")
@RequestMapping(
        path = ["/posts"],
        produces = [(MediaType.APPLICATION_JSON_VALUE)]
)
@RestController
class PostApi {

    @Autowired
    private lateinit var postService: PostService


    /*
    This service will communicate with the "user-service" one.
    We inject the ip address here as a variable, as we ll
    change it in the tests.
 */

    @Value("\${userServiceAddress}")
    private lateinit var userServiceAddress: String


    @ApiOperation("*")
    @GetMapping(produces = [(MediaType.APPLICATION_JSON_VALUE)])
    fun getAll(
            @RequestParam("ignoreSession", required = false)
            ignoreSession: Boolean?,

            @CookieValue("SESSION", required = false)
            cookie: String?,

            @ApiParam("Offset in the list of news")
            @RequestParam("offset", defaultValue = "0")
            offset: Int,

            @ApiParam("Limit of news in a single retrieved page")
            @RequestParam("limit", defaultValue = "10")
            limit: Int,
            principal: Principal
    ): ResponseEntity<WrappedResponse<PageDto<PostDto>>> {
        val uri = UriComponentsBuilder
                .fromUriString("http://${userServiceAddress.trim()}/users/${principal.name}")
                .build().toUri()

        val client = RestTemplate()


        val requestHeaders = HttpHeaders()
        if (ignoreSession == null || !ignoreSession) {
            requestHeaders.add("cookie", "SESSION=$cookie")
        }
        val requestEntity = HttpEntity(null, requestHeaders)

        // Got some inspiration from https://stackoverflow.com/questions/52581729/create-instance-of-spring´s-parameterizedtypereference-in-kotlin
        val response = try {
            client.exchange(uri, HttpMethod.GET, requestEntity, object: ParameterizedTypeReference<WrappedResponse<UserDto>>(){})
        } catch (e: HttpStatusCodeException) {
            return if (e.statusCode.value() == 403) {
                return ResponseEntity.status(400).body(
                        WrappedResponse<PageDto<PostDto>>(
                                message = e.message,
                                code = 400
                        ).validated())
            } else {
                return ResponseEntity.status(500).body(
                        WrappedResponse<PageDto<PostDto>>(
                                message = e.message,
                                code = 500
                        ).validated())
            }
        }

        val wrapper = response.body as WrappedResponse
        val user = wrapper.data as UserDto


        if (user === null) {
            return ResponseEntity.status(400).body(
                    WrappedResponse<PageDto<PostDto>>(
                            message = "User was not found",
                            code = 400
                    )
                            .validated())
        }

        val maxPageLimit = 50
        val maxFromDb = 1000
        val onDbWithId = postService.getNumberOfPosts(user.friends)

        if (offset < 0 || limit < 1 || limit > maxPageLimit || (offset + limit) > maxFromDb || offset > onDbWithId) {
            return ResponseEntity.status(400).body(
                    WrappedResponse<PageDto<PostDto>>(code = 400)
                            .validated())
        }

        var builder = UriComponentsBuilder
                .fromPath("/posts")

        val posts = postService.getAll(principal.name, user.friends, offset, limit, onDbWithId, maxPageLimit, builder)
        return ResponseEntity.ok(
                WrappedResponse(
                        code = 200,
                        data = posts
                ).validated()
        )
    }


    @ApiOperation("*")
    @GetMapping(path = ["/{id}"], produces = [(MediaType.APPLICATION_JSON_VALUE)])
    fun getById(
            @ApiParam("Unique post id")
            @PathVariable("id") postId: String
    ): ResponseEntity<WrappedResponse<PostDto>> {
        val id: Long

        try {
            id = postId.toLong()
        } catch (nfe: NumberFormatException) {
            return ResponseEntity.status(400).body(
                    WrappedResponse<PostDto>(
                            code = 400,
                            message = "$postId is not a valid id"
                    ).validated()
            )
        }

        val posts = postService.getById(id)


        // todo only retrieve posts of user or posts of friends

        if (posts === null) {
            return ResponseEntity.status(404).body(
                    WrappedResponse<PostDto>(
                            code = 404,
                            message = "Post cannot be null"
                    ).validated()
            )
        }

        return ResponseEntity.ok(
                WrappedResponse(
                        code = 200,
                        data = posts
                ).validated()
        )
    }


    @ApiOperation("Create a post")
    @PostMapping(consumes = [(MediaType.APPLICATION_JSON_UTF8_VALUE)])
    fun createPost(@ApiParam("Information for new post")
                   @RequestBody postDto: PostDto,
                   @RequestParam("ignoreSession", required = false) ignoreSession: Boolean?,
                   @CookieValue("SESSION", required = false)
                   cookie: String?,
                   user: Principal): ResponseEntity<WrappedResponse<Unit>> {


        if (user.name === null) {
            return ResponseEntity.status(400).body(
                    WrappedResponse<Unit>(code = 400)
                            .validated())
        }


        val id = postService.createPost(postDto, user.name)

        if (id == -1L) {
            return ResponseEntity.status(400).body(
                    WrappedResponse<Unit>(code = 400, message = "Unable to create post")
                            .validated())
        }

        return ResponseEntity.created(URI.create("/posts/$id")).body(
                WrappedResponse<Unit>(code = 201, message = "Post was created").validated())
    }

    @ApiOperation("Delete a movie by id")
    @DeleteMapping(path = ["/{id}"])
    fun deleteById(
            @ApiParam("post id")
            @PathVariable("id")
            postId: String
    ): ResponseEntity<WrappedResponse<Unit>> {
        val id: Long

        try {
            id = postId.toLong()
        } catch (nfe: NumberFormatException) {
            return ResponseEntity.status(400).body(
                    WrappedResponse<Unit>(
                            code = 400,
                            message = "$postId is not a valid id"
                    ).validated()
            )
        }

        val isDeleted = postService.deleteById(id)

        if (!isDeleted)
            return ResponseEntity.status(404).body(
                    WrappedResponse<Unit>(
                            code = 404,
                            message = "$postId does not exist"
                    ).validated()
            )

        return ResponseEntity.status(204).body(
                WrappedResponse<Unit>(
                        code = 204,
                        message = "Post with id=$postId was deleted successfully"
                ).validated()
        )
    }

}