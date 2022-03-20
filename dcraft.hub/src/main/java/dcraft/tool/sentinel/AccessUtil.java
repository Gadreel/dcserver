package dcraft.tool.sentinel;

import dcraft.struct.ListStruct;

/*
    Manage AWS security groups for dev and public access
 */
public class AccessUtil {
    // TODO this is what is needed
    static public ListStruct accessPlan() {
        /*
            AWS
                SecGroup
                    RuleId + Port       -- support so that rule id can be missing  (rules needed)
                        Address

         */

        return null;
    }

    // TODO this is what we have on record
    // can be subtracted from the plan above or used in a verification system in periodic checks
    static public ListStruct accessRecord() {
        /*
            AWS
                SecGroup
                    RuleId + Port       -- support so that rule id can be mssing
                        Address


        SELECT daa.Id AWSAccountId, daa.Title AWSAccountTitle, daa.Alias AWSAccountAlias, dasg.SecurityGroupId AWSSecurityGroupId,
       usg.IPv4RuleId, usg.Port, uir.IPv4Address, uir.Expires RuleExpires
FROM dca_user_ip_rules uir
    INNER JOIN dca_user_security_group usg ON (usg.UserRuleId = uir.Id)
    INNER JOIN dca_deployment_security_group ddsg ON (usg.SecurityGroupId = ddsg.SecurityGroupId)
    INNER JOIN dca_aws_security_group dasg ON (ddsg.SecurityGroupId = dasg.Id)
    INNER JOIN dca_aws_account daa ON (dasg.AccountId = daa.Id)
WHERE uir.UserId = 1
ORDER BY daa.Id, dasg.Id;


         */


        return null;
    }

    // TODO take a plan and verify it is set in DB, mark/add entries as missing if so (add only to the plan, not to db)
    static public void verifyRecord() {

    }

    // TODO take a plan and verify it is set in AWS, mark/add entries as missing if so (add only to the plan, not to AWS)
    static public void verifyAccess() {

    }

    // TODO take a list and update any missing access
    static public void updateAccess() {
        /*
            AWS
                SecGroup
                    RuleId + Port       -- if missing rule id then add to aws and save in db
                        Address

         */

    }
}
