package no.breale17.post.entity

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import javax.validation.constraints.NotEmpty

@Entity
@Table(name = "POSTS")
class PostEntity(
        var title: String? = null,
        var message: String? = null,
        var date: Long? = null,
        @get:NotEmpty
        var userId: String? = null,
        @get:Id
        @get:GeneratedValue
        var id: Long? = null
)