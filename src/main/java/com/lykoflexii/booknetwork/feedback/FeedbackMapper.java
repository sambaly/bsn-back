package com.lykoflexii.booknetwork.feedback;

import com.lykoflexii.booknetwork.book.Book;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class FeedbackMapper {
  public Feedback tofeedback(FeedbackRequest request) {
    return Feedback
            .builder()
            .note(request.note())
            .comment(request.comment())
            .book(
                    Book
                            .builder()
                            .id(request.bookId())
                            .build()
            )
            .build();
  }

  public FeedbackResponse toFeedbackResponse(Feedback feedback, Integer id) {
    return FeedbackResponse
            .builder()
            .note(feedback.getNote())
            .comment(feedback.getComment())
            .ownFeedback(Objects.equals(feedback.getCreatedBy(), id))
            .build();
  }
}
