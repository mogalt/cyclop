package org.cyclop.service.completion.parser.createindex;

import javax.inject.Named;
import org.cyclop.model.CqlNotSupported;
import org.cyclop.service.completion.parser.NotSupportedCompletion;

/**
 * @author Maciej Miklas
 */
@Named("createindex.CreateCompletion")
public class CreateCompletion extends NotSupportedCompletion {

    public CreateCompletion() {
        super(new CqlNotSupported("create"));
    }

    @Override
    protected String getNotSupportedText() {
        return "create index";
    }
}
