package org.cyclop.service.completion.parser.update;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import org.cyclop.model.CqlCompletion;
import org.cyclop.model.CqlKeyword;
import org.cyclop.model.CqlQuery;
import org.cyclop.service.completion.parser.template.AfterTableNameCompletionTemplate;

/**
 * @author Maciej Miklas
 */
@Named("update.AfterUpdateCompletion")
public class AfterUpdateCompletion extends AfterTableNameCompletionTemplate {

    private CqlCompletion completion;

    public AfterUpdateCompletion() {
        super(CqlKeyword.Def.UPDATE.value);
    }

    @PostConstruct
    public void init() {
        completion = CqlCompletion.Builder.naturalOrder().all(CqlKeyword.Def.USING_TTL.value).all(CqlKeyword.Def
                .USING_TIMESTAMP.value).all(CqlKeyword.Def.SET.value).build();
    }

    @Override
    public CqlCompletion getCompletion(CqlQuery query) {
        return completion;
    }

}
